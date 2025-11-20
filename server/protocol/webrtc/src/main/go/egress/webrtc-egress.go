// based off of https://github.com/pion/example-webrtc-applications/blob/master/rtmp-to-webrtc/ & https://github.com/pion/example-webrtc-applications/blob/master/play-from-disk-h264/
package main

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"os/exec"
	"os/signal"
	"strconv"

	"github.com/pion/rtp"
	"github.com/pion/rtp/codecs"
	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media/samplebuilder"
)

func main() { // nolint
	// Configure TCP ICE
	settingEngine := webrtc.SettingEngine{}
	settingEngine.SetNetworkTypes([]webrtc.NetworkType{
		webrtc.NetworkTypeTCP4,
		webrtc.NetworkTypeTCP6,
	})

	iceTcpPort, err := strconv.Atoi(os.Args[2])
	if err != nil {
		panic(err)
	}

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

	tcpMux := webrtc.NewICETCPMux(nil, tcpListener, 8)
	settingEngine.SetICETCPMux(tcpMux)

	api := webrtc.NewAPI(webrtc.WithSettingEngine(settingEngine))

	// Prepare the configuration
	config := webrtc.Configuration{
		// ICEServers: []webrtc.ICEServer{
		// 	{
		// 		URLs: []string{"stun:stun.l.google.com:19302"},
		// 	},
		// },
	}

	// Create a new RTCPeerConnection
	peerConnection, err := api.NewPeerConnection(config)
	if err != nil {
		panic(err)
	}
	defer func() {
		if cErr := peerConnection.Close(); cErr != nil {
			fmt.Fprintf(os.Stderr, "cannot close peerConnection: %v\n", cErr)
		}
	}()

	// Create a Audio Track
	audioTrack, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeOpus}, "audio", "pion+quark") // nolint
	if err != nil {
		panic(err)
	}

	// Handle RTCP, see rtcpReader for why
	rtpSender, err := peerConnection.AddTrack(audioTrack)
	if err != nil {
		panic(err)
	}
	rtcpReader(rtpSender)

	// Create a Video Track
	videoTrack, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeVP8}, "video", "pion+quark") // nolint
	if err != nil {
		panic(err)
	}

	// Handle RTCP, see rtcpReader for why
	rtpSender, err = peerConnection.AddTrack(videoTrack)
	if err != nil {
		panic(err)
	}
	rtcpReader(rtpSender)

	// Set the handler for Peer connection state
	// This will notify you when the peer has connected/disconnected
	peerConnection.OnICEConnectionStateChange(func(connectionState webrtc.ICEConnectionState) {
		fmt.Fprintf(os.Stderr, "state:%s\n", connectionState.String())
	})

	// Wait for the offer to be pasted
	offer := webrtc.SessionDescription{}
	decode(os.Args[5], &offer)

	// Set the remote SessionDescription
	if err = peerConnection.SetRemoteDescription(offer); err != nil {
		panic(err)
	}

	// Create answer
	answer, err := peerConnection.CreateAnswer(nil)
	if err != nil {
		panic(err)
	}

	// Create channel that is blocked until ICE Gathering is complete
	gatherComplete := webrtc.GatheringCompletePromise(peerConnection)

	// Sets the LocalDescription, and starts our UDP listeners
	if err = peerConnection.SetLocalDescription(answer); err != nil {
		panic(err)
	}

	// Block until ICE Gathering is complete, disabling trickle ICE
	// we do this because we only can exchange one signaling message
	// in a production application you should exchange ICE Candidates via OnICECandidate
	<-gatherComplete

	// Output the answer in base64 so we can paste it in browser
	fmt.Fprintf(os.Stderr, "answer:%s\n", encode(peerConnection.LocalDescription()))

	udpVideoPort, err := strconv.Atoi(os.Args[3])
	if err != nil {
		panic(err)
	}

	udpAudioPort, err := strconv.Atoi(os.Args[4])
	if err != nil {
		panic(err)
	}

	go rtpToTrack(videoTrack, &codecs.VP8Packet{}, 90000, udpVideoPort)
	go rtpToTrack(audioTrack, &codecs.OpusPacket{}, 48000, udpAudioPort)

	// Start FFmpeg
	cmd := exec.Command(
		"ffmpeg",
		"-hide_banner",
		// "-loglevel", "level+fatal",
		"-fflags", "+nobuffer+flush_packets",
		"-f", "flv",
		"-i", "-",
		"-an", "-c:v", "libvpx",
		"-deadline", "1",
		"-g", "10",
		"-error-resilient", "1",
		"-auto-alt-ref", "1",
		"-f", "rtp",
		"rtp://127.0.0.1:"+strconv.Itoa(udpVideoPort)+"?pkt_size=1200",
		"-vn", "-c:a", "libopus",
		"-f", "rtp",
		"rtp://127.0.0.1:"+strconv.Itoa(udpAudioPort)+"?pkt_size=1200")
	cmd.Stdin = os.Stdin
	cmd.Stderr = os.Stderr

	if err := cmd.Start(); err != nil {
		panic(err)
	}

	closed := make(chan os.Signal, 1)
	signal.Notify(closed, os.Interrupt)
	<-closed

	if err := peerConnection.Close(); err != nil {
		panic(err)
	}
	cmd.Process.Kill()
	os.Exit(0)
}

// Listen for incoming packets on a port and write them to a Track.
func rtpToTrack(track *webrtc.TrackLocalStaticSample, depacketizer rtp.Depacketizer, sampleRate uint32, port int) {
	// Open a UDP Listener for RTP Packets on port 5004
	listener, err := net.ListenUDP("udp", &net.UDPAddr{IP: net.ParseIP("127.0.0.1"), Port: port})
	if err != nil {
		panic(err)
	}
	defer func() {
		if err = listener.Close(); err != nil {
			panic(err)
		}
	}()

	sampleBuffer := samplebuilder.New(10, depacketizer, sampleRate)

	// Read RTP packets forever and send them to the WebRTC Client
	for {
		inboundRTPPacket := make([]byte, 1500) // UDP MTU
		packet := &rtp.Packet{}

		n, _, err := listener.ReadFrom(inboundRTPPacket)
		if err != nil {
			panic(fmt.Sprintf("error during read: %s", err))
		}

		if err = packet.Unmarshal(inboundRTPPacket[:n]); err != nil {
			panic(err)
		}

		sampleBuffer.Push(packet)
		for {
			sample := sampleBuffer.Pop()
			if sample == nil {
				break
			}

			if writeErr := track.WriteSample(*sample); writeErr != nil {
				panic(writeErr)
			}
		}
	}
}

// Read incoming RTCP packets
// Before these packets are returned they are processed by interceptors. For things
// like NACK this needs to be called.
func rtcpReader(rtpSender *webrtc.RTPSender) {
	go func() {
		rtcpBuf := make([]byte, 1500)
		for {
			if _, _, rtcpErr := rtpSender.Read(rtcpBuf); rtcpErr != nil {
				return
			}
		}
	}()
}

// JSON encode a SessionDescription.
func encode(obj *webrtc.SessionDescription) string {
	b, err := json.Marshal(obj)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}

	return string(b)
}

// Decode a base64 and unmarshal JSON into a SessionDescription.
func decode(in string, obj *webrtc.SessionDescription) {
	b, err := base64.StdEncoding.DecodeString(in)
	if err != nil {
		panic(err)
	}

	if err = json.Unmarshal(b, obj); err != nil {
		panic(err)
	}
}
