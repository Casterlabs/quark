// based off of https://github.com/pion/example-webrtc-applications/blob/master/rtmp-to-webrtc/ & https://github.com/pion/example-webrtc-applications/blob/master/play-from-disk-h264/
package main

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"os/signal"
	"strconv"
	"time"

	"github.com/pion/webrtc/v4"
	"github.com/pion/webrtc/v4/pkg/media"
	"github.com/pion/webrtc/v4/pkg/media/h264reader"
	"github.com/pion/webrtc/v4/pkg/media/oggreader"
)

func main() { // nolint
	// Configure TCP ICE
	settingEngine := webrtc.SettingEngine{}
	settingEngine.SetNetworkTypes([]webrtc.NetworkType{
		webrtc.NetworkTypeUDP4,
		webrtc.NetworkTypeUDP6,
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
		ICEServers: []webrtc.ICEServer{
			{
				URLs: []string{"stun:stun.l.google.com:19302"},
			},
		},
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

	// Create a Video Track
	videoTrack, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeH264}, "video", "pion+quark") // nolint
	if err != nil {
		panic(err)
	}

	// Handle RTCP, see rtcpReader for why
	rtpSender, err := peerConnection.AddTrack(videoTrack)
	if err != nil {
		panic(err)
	}
	rtcpReader(rtpSender)

	// Create a Audio Track
	audioTrack, err := webrtc.NewTrackLocalStaticSample(webrtc.RTPCodecCapability{MimeType: webrtc.MimeTypeOpus}, "audio", "pion+quark") // nolint
	if err != nil {
		panic(err)
	}

	// Handle RTCP, see rtcpReader for why
	rtpSender, err = peerConnection.AddTrack(audioTrack)
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
	decode(os.Args[3], &offer)

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

	closed := make(chan os.Signal, 1)
	signal.Notify(closed, os.Interrupt)

	// Start FFmpeg
	cmd := exec.Command(
		"ffmpeg",
		"-hide_banner",
		"-loglevel", "level+fatal",
		"-fflags", "+nobuffer+flush_packets",
		"-f", "flv",
		"-i", "pipe:0",

		"-map", "0:v",
		"-map", "0:a",
		"-c:v", "copy",
		"-c:a", "libopus",
		"-b:a", "320k",
		"-f", "tee",
		"[select=v:f=h264:bsfs/v=h264_mp4toannexb]pipe:1|[select=a:f=ogg:page_duration=10000]pipe:2")
	cmd.Stdin = os.Stdin

	h264Pipe, err := cmd.StdoutPipe()
	if err != nil {
		panic(err)
	}

	oggPipe, err := cmd.StderrPipe()
	if err != nil {
		panic(err)
	}

	if err := cmd.Start(); err != nil {
		panic(err)
	}

	go handleH264(videoTrack, h264Pipe)
	go handleOpus(audioTrack, oggPipe)

	<-closed

	cmd.Process.Kill()

	if err := peerConnection.Close(); err != nil {
		panic(err)
	}

	os.Exit(0)
}

func handleH264(track *webrtc.TrackLocalStaticSample, pipe io.ReadCloser) {
	// Open a H264 reader on the file.
	h264, h264Err := h264reader.NewReader(pipe)
	if h264Err != nil {
		panic(h264Err)
	}

	h264FrameDuration := time.Millisecond * 100 // junk value
	// var lastFrameTime time.Time

	for {
		nal, h264Err := h264.NextNAL()
		if errors.Is(h264Err, io.EOF) {
			fmt.Printf("All video frames parsed and sent")
			break
		}
		if h264Err != nil {
			panic(h264Err)
		}

		// if len(nal.Data) > 0 {
		// 	payload := nal.Data

		// 	nalType := payload[0] & 0x1F
		// 	if nalType == 1 || nalType == 5 {
		// 		now := time.Now()
		// 		if !lastFrameTime.IsZero() {
		// 			h264FrameDuration = now.Sub(lastFrameTime)

		// 			// Latch to common FPS
		// 			for _, fps := range []time.Duration{25, 30, 50, 60, 90, 100, 120} {
		// 				target := time.Second / fps
		// 				diff := h264FrameDuration - target
		// 				if diff < 0 {
		// 					diff = -diff
		// 				}
		// 				if diff < 2*time.Millisecond {
		// 					h264FrameDuration = target
		// 					break
		// 				}
		// 			}

		// 			// if h264FrameDuration > 0 {
		// 			// 	fmt.Fprintf(os.Stderr, "Predicted FPS: %.2f (Duration: %v)\n", 1.0/h264FrameDuration.Seconds(), h264FrameDuration)
		// 			// }
		// 		}
		// 		lastFrameTime = now
		// 	}
		// }

		if h264Err = track.WriteSample(media.Sample{Data: nal.Data, Duration: h264FrameDuration}); h264Err != nil {
			panic(h264Err)
		}
	}
}

func handleOpus(track *webrtc.TrackLocalStaticSample, pipe io.ReadCloser) {
	// Open on oggfile in non-checksum mode.
	ogg, _, oggErr := oggreader.NewWith(pipe)
	if oggErr != nil {
		panic(oggErr)
	}

	// Keep track of last granule, the difference is the amount of samples in the buffer
	var lastGranule uint64

	for {
		pageData, pageHeader, oggErr := ogg.ParseNextPage()
		if errors.Is(oggErr, io.EOF) {
			fmt.Printf("All audio pages parsed and sent")
			break
		}

		if oggErr != nil {
			panic(oggErr)
		}

		// The amount of samples is the difference between the last and current timestamp
		sampleCount := float64(pageHeader.GranulePosition - lastGranule)
		lastGranule = pageHeader.GranulePosition
		sampleDuration := time.Duration((sampleCount/48000)*1000) * time.Millisecond

		if oggErr = track.WriteSample(media.Sample{Data: pageData, Duration: sampleDuration}); oggErr != nil {
			panic(oggErr)
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
