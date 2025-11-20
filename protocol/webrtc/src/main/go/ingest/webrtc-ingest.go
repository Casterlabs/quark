// based off of https://github.com/pion/example-webrtc-applications/blob/master/save-to-webm
package main

import (
	"bufio"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"os/signal"
	"strconv"
	"strings"
	"time"

	"github.com/at-wat/ebml-go/webm"
	"github.com/pion/interceptor/pkg/jitterbuffer"
	"github.com/pion/rtcp"
	"github.com/pion/rtp"
	"github.com/pion/rtp/codecs"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media/samplebuilder"
)

const (
	naluTypeBitmask = 0b11111
	naluTypeSPS     = 7
)

func main() {
	ctx := newSessionContext()
	peerConnection := createWebRTCConn(ctx)

	closed := make(chan os.Signal, 1)
	signal.Notify(closed, os.Interrupt)
	<-closed

	if err := peerConnection.Close(); err != nil {
		panic(err)
	}
	ctx.Close()
}

type sessionContext struct {
	audioWriter, videoWriter       webm.BlockWriteCloser
	audioBuilder                   *samplebuilder.SampleBuilder
	audioTimestamp, videoTimestamp time.Duration

	h264JitterBuffer   *jitterbuffer.JitterBuffer
	lastVideoTimestamp uint32

	flvRemuxer *exec.Cmd
}

func newSessionContext() *sessionContext {
	cmd := exec.Command(
		"ffmpeg",
		"-hide_banner",
		"-loglevel", "level+fatal",
		"-fflags", "+nobuffer+flush_packets",
		"-f", "matroska",
		"-i", "-",
		"-c:v", "copy",
		"-c:a", "aac",
		"-ar", "48000",
		"-ac", "2",
		"-b:a", "320k",
		"-f", "flv",
		"-")
	cmd.Stderr = os.Stderr
	cmd.Stdout = os.Stdout

	remuxStdin, err := cmd.StdinPipe()
	if err != nil {
		panic(err)
	}

	err = cmd.Start()
	if err != nil {
		panic(err)
	}

	ws, err := webm.NewSimpleBlockWriter(remuxStdin,
		[]webm.TrackEntry{
			{
				Name:            "Video",
				TrackNumber:     1,
				TrackUID:        1,
				CodecID:         "V_MPEG4/ISO/AVC",
				TrackType:       1,
				DefaultDuration: 33333333,
				Video: &webm.Video{
					PixelWidth:  uint64(1280), // nolint
					PixelHeight: uint64(720),  // nolint
				},
			},
			{
				Name:            "Audio",
				TrackNumber:     2,
				TrackUID:        2,
				CodecID:         "A_OPUS",
				TrackType:       2,
				DefaultDuration: 20000000,
				Audio: &webm.Audio{
					SamplingFrequency: 48000.0,
					Channels:          2,
				},
			},
		})
	if err != nil {
		panic(err)
	}

	return &sessionContext{
		audioBuilder:     samplebuilder.New(10, &codecs.OpusPacket{}, 48000),
		h264JitterBuffer: jitterbuffer.New(),
		videoWriter:      ws[0],
		audioWriter:      ws[1],
		flvRemuxer:       cmd,
	}
}

func (c *sessionContext) Close() {
	fmt.Fprintf(os.Stderr, "Finalizing webm...\n")
	if c.audioWriter != nil {
		if err := c.audioWriter.Close(); err != nil {
			panic(err)
		}
	}
	if c.videoWriter != nil {
		if err := c.videoWriter.Close(); err != nil {
			panic(err)
		}
	}
	if c.flvRemuxer != nil {
		if err := c.flvRemuxer.Process.Kill(); err != nil {
			panic(err)
		}
	}
}

func (c *sessionContext) PushOpus(rtpPacket *rtp.Packet) {
	c.audioBuilder.Push(rtpPacket)

	for {
		sample := c.audioBuilder.Pop()
		if sample == nil {
			return
		}

		c.audioTimestamp += sample.Duration
		if _, err := c.audioWriter.Write(true, int64(c.audioTimestamp/time.Millisecond), sample.Data); err != nil {
			panic(err)
		}
	}
}

func (c *sessionContext) PushH264(rtpPacket *rtp.Packet) { // nolint
	c.h264JitterBuffer.Push(rtpPacket)

	pkt, err := c.h264JitterBuffer.Peek(true)
	if err != nil {
		return
	}

	pkts := []*rtp.Packet{pkt}
	for {
		pkt, err = c.h264JitterBuffer.PeekAtSequence(pkts[len(pkts)-1].SequenceNumber + 1)
		if err != nil {
			return
		}

		// We have popped a whole frame, lets write it
		if pkts[0].Timestamp != pkt.Timestamp {
			break
		}

		pkts = append(pkts, pkt)
	}

	h264Packet := &codecs.H264Packet{}
	data := []byte{}
	for i := range pkts {
		if _, err = c.h264JitterBuffer.PopAtSequence(pkts[i].SequenceNumber); err != nil {
			// panic(err)
			continue
		}

		out, err := h264Packet.Unmarshal(pkts[i].Payload)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Error unmarshaling H264 packet: %v\n", err)
			continue
		}
		data = append(data, out...)
	}

	videoKeyframe := (data[4] & naluTypeBitmask) == naluTypeSPS

	samples := uint32(0)
	if c.lastVideoTimestamp != 0 {
		samples = pkts[0].Timestamp - c.lastVideoTimestamp
	}
	c.lastVideoTimestamp = pkts[0].Timestamp

	c.videoTimestamp += time.Duration(float64(samples) / float64(90000) * float64(time.Second))
	if _, err := c.videoWriter.Write(videoKeyframe, int64(c.videoTimestamp/time.Millisecond), data); err != nil {
		panic(err)
	}
}

func createWebRTCConn(saver *sessionContext) *webrtc.PeerConnection { // nolint
	// Everything below is the Pion WebRTC API! Thanks for using it ❤️.

	settingEngine := webrtc.SettingEngine{}

	// Enable support only for TCP ICE candidates.
	settingEngine.SetNetworkTypes([]webrtc.NetworkType{
		webrtc.NetworkTypeTCP4,
		webrtc.NetworkTypeTCP6,
	})

	iceTcpPort, err := strconv.Atoi(os.Args[2])
	if err != nil {
		panic(err)
	}

	// We want to advertise a single IP for all our ICE TCP connections
	if len(os.Args[1]) > 0 {
		settingEngine.SetNAT1To1IPs([]string{os.Args[1]}, webrtc.ICECandidateTypeHost)
	}

	tcpListener, err := net.ListenTCP("tcp", &net.TCPAddr{
		IP:   net.IPv4zero,
		Port: iceTcpPort,
	})
	if err != nil {
		panic(err)
	}

	fmt.Fprintf(os.Stderr, "Listening for ICE TCP at %s\n", tcpListener.Addr())

	// Prepare the configuration
	config := webrtc.Configuration{
		// ICEServers: []webrtc.ICEServer{
		// 	{
		// 		URLs: []string{"stun:stun.l.google.com:19302"},
		// 	},
		// },
	}

	tcpMux := webrtc.NewICETCPMux(nil, tcpListener, 8)
	settingEngine.SetICETCPMux(tcpMux)

	// Create a MediaEngine object to configure the supported codec
	mediaEngine := &webrtc.MediaEngine{}

	// Setup the codecs you want to use.
	// This example supports VP8 or H264. Some browsers may only support one (or the other)
	if err := mediaEngine.RegisterCodec(webrtc.RTPCodecParameters{
		RTPCodecCapability: webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeH264, ClockRate: 90000},
		PayloadType:        98,
	}, webrtc.RTPCodecTypeVideo); err != nil {
		panic(err)
	}
	if err := mediaEngine.RegisterCodec(webrtc.RTPCodecParameters{
		RTPCodecCapability: webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeOpus, ClockRate: 48000, Channels: 2},
		PayloadType:        111,
	}, webrtc.RTPCodecTypeAudio); err != nil {
		panic(err)
	}

	// Create the API object with the MediaEngine
	api := webrtc.NewAPI(webrtc.WithMediaEngine(mediaEngine), webrtc.WithSettingEngine(settingEngine))

	// Create a new RTCPeerConnection
	peerConnection, err := api.NewPeerConnection(config)
	if err != nil {
		panic(err)
	}

	// Set a handler for when a new remote track starts, this handler copies inbound RTP packets,
	// replaces the SSRC and sends them back
	peerConnection.OnTrack(func(track *webrtc.TrackRemote, _ *webrtc.RTPReceiver) {
		if track.Kind() == webrtc.RTPCodecTypeVideo {
			// Send a PLI on an interval so that the publisher is pushing a keyframe every rtcpPLIInterval
			go func() {
				ticker := time.NewTicker(time.Second * 3)
				for range ticker.C {
					errSend := peerConnection.WriteRTCP([]rtcp.Packet{&rtcp.PictureLossIndication{MediaSSRC: uint32(track.SSRC())}})
					if errSend != nil {
						fmt.Println(errSend)
					}
				}
			}()
		}

		fmt.Fprintf(os.Stderr, "Track has started, of type %d: %s \n", track.PayloadType(), track.Codec().RTPCodecCapability.MimeType)
		for {
			// Read RTP packets being sent to Pion
			rtp, _, readErr := track.ReadRTP()
			if readErr != nil {
				if errors.Is(readErr, io.EOF) {
					return
				}
				panic(readErr)
			}

			switch track.Codec().MimeType {
			case webrtc.MimeTypeOpus:
				saver.PushOpus(rtp)
			case webrtc.MimeTypeH264:
				saver.PushH264(rtp)
			}
		}
	})
	// Set the handler for ICE connection state
	// This will notify you when the peer has connected/disconnected
	peerConnection.OnICEConnectionStateChange(func(connectionState webrtc.ICEConnectionState) {
		fmt.Fprintf(os.Stderr, "state:%s\n", connectionState.String())
	})

	// Wait for the offer to be pasted
	offer := webrtc.SessionDescription{}
	decode(readUntilNewline(), &offer)

	// Set the remote SessionDescription
	err = peerConnection.SetRemoteDescription(offer)
	if err != nil {
		panic(err)
	}

	// Create an answer
	answer, err := peerConnection.CreateAnswer(nil)
	if err != nil {
		panic(err)
	}

	// Create channel that is blocked until ICE Gathering is complete
	gatherComplete := webrtc.GatheringCompletePromise(peerConnection)

	// Sets the LocalDescription, and starts our UDP listeners
	err = peerConnection.SetLocalDescription(answer)
	if err != nil {
		panic(err)
	}

	// Block until ICE Gathering is complete, disabling trickle ICE
	// we do this because we only can exchange one signaling message
	// in a production application you should exchange ICE Candidates via OnICECandidate
	<-gatherComplete

	// Output the answer in base64 so we can paste it in browser
	fmt.Fprintf(os.Stderr, "answer:%s\n", encode(peerConnection.LocalDescription()))

	return peerConnection
}

// Read from stdin until we get a newline.
func readUntilNewline() (in string) {
	var err error

	r := bufio.NewReader(os.Stdin)
	for {
		in, err = r.ReadString('\n')
		if err != nil && !errors.Is(err, io.EOF) {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}

		if in = strings.TrimSpace(in); len(in) > 0 {
			break
		}
	}

	return
}

func encode(obj *webrtc.SessionDescription) string {
	b, err := json.Marshal(obj)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	return string(b)
}

func decode(in string, obj *webrtc.SessionDescription) {
	if err := json.Unmarshal([]byte(in), obj); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
