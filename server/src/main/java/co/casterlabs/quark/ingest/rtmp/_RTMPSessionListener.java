package co.casterlabs.quark.ingest.rtmp;

import java.io.IOException;
import java.util.Arrays;

import co.casterlabs.flv4j.actionscript.amf0.String0;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.audio.FLVAudioTagData;
import co.casterlabs.flv4j.flv.tags.script.FLVScriptTagData;
import co.casterlabs.flv4j.flv.tags.video.FLVVideoPayload;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageAudio;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageData0;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageUserControl;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageVideo;
import co.casterlabs.flv4j.rtmp.chunks.control.RTMPSetBufferLengthControlMessage;
import co.casterlabs.flv4j.rtmp.chunks.control.RTMPStreamEOFControlMessage;
import co.casterlabs.flv4j.rtmp.net.NetStatus;
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.auth.Auth;
import co.casterlabs.quark.auth.AuthenticationException;
import co.casterlabs.quark.auth.User;
import co.casterlabs.quark.session.FLVSequence;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionListener;

class _RTMPSessionListener extends SessionListener {
    private static final String0 SET_DATA_FRAME = new String0("@setDataFrame");
    private static final int BUFFER_LENGTH = 1000; // arbitrary?

    private final _RTMPConnection rtmp;
    private Session session;

    private String fid;
    private boolean hasGottenSequence = false;

    _RTMPSessionListener(_RTMPConnection rtmp) {
        this.rtmp = rtmp;
    }

    void play(String name) {
        if (this.rtmp.state != _RTMPState.AUTHENTICATING) {
            this.rtmp.logger.debug("Closing, client sent play() during state %s", this.rtmp.state);
            this.rtmp.stream.setStatus(NetStatus.NS_PLAY_FAILED);
            this.rtmp.close(true);
            return;
        }

        try {
            // Using the app field is a bit hacky, but it works :D
            User user = Auth.authenticate(this.rtmp.connectArgs.app());
            user.checkPlayback(name);
            this.fid = user.id();
        } catch (AuthenticationException e) {
            this.rtmp.logger.debug("Closing, unauthorized: %s", e.getMessage());
            this.rtmp.stream.setStatus(NetStatus.NS_PLAY_FAILED);
            this.rtmp.close(true);
            return;
        }

        this.session = Sessions.getSession(name, false);

        if (this.session == null) {
            this.rtmp.logger.debug("Closing, no session.");
            this.rtmp.stream.setStatus(NetStatus.NS_PLAY_FAILED);
            this.rtmp.close(true);
        } else {
            this.rtmp.logger.debug("Playback allowed.");

            try {
                this.rtmp.stream.sendMessage(
                    0,
                    new RTMPMessageUserControl(
                        new RTMPSetBufferLengthControlMessage(
                            this.rtmp.stream.id(),
                            BUFFER_LENGTH
                        )
                    )
                );

                this.rtmp.state = _RTMPState.PLAYING;
                this.session.addAsyncListener(this);
            } catch (IOException | InterruptedException ignored) {
                this.rtmp.close(true);
            }
        }
    }

    private void writeOut(Session session, FLVTag tag) {
        try {
            int dts32 = (int) (tag.timestamp() & 0xFFFFFFFFL);

            if (tag.data() instanceof FLVAudioTagData audio) {
                this.rtmp.stream.sendMessage(dts32, new RTMPMessageAudio(audio));
            } else if (tag.data() instanceof FLVVideoPayload video) {
                this.rtmp.stream.sendMessage(dts32, new RTMPMessageVideo(video));
            } else if (tag.data() instanceof FLVScriptTagData script) {
                this.rtmp.stream.sendMessage(
                    dts32,
                    new RTMPMessageData0(
                        Arrays.asList(
                            SET_DATA_FRAME,
                            new String0(script.methodName()),
                            script.value()
                        )
                    )
                );
            }
        } catch (IOException | InterruptedException e) {
            this.rtmp.close(true); // junk value.
        }
    }

    void closeConnection() {
        if (this.session == null) return;

        this.session.removeListener(this);

        try {
            this.rtmp.stream.sendMessage(
                0,
                new RTMPMessageUserControl(
                    new RTMPStreamEOFControlMessage(
                        this.rtmp.stream.id()
                    )
                )
            );
        } catch (IOException | InterruptedException ignored) {}
    }

    /* ---------------- */
    /*  Quark Listener  */
    /* ---------------- */

    @Override
    public void onSequence(Session session, FLVSequence seq) {
        this.hasGottenSequence = true;
        for (FLVTag tag : seq.tags()) {
            this.writeOut(session, tag);
        }
    }

    @Override
    public void onTag(Session session, FLVTag tag) {
        if (!this.hasGottenSequence) {
            return;
        }

        this.writeOut(session, tag);
    }

    @Override
    public String fid() {
        return this.fid;
    }

    @Override
    public void onClose(Session session) {
        this.rtmp.close(true); // junk value.
    }

    @Override
    public Type type() {
        return Type.RTMP_PLAYBACK;
    }

}
