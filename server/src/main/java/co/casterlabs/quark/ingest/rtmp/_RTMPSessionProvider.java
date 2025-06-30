package co.casterlabs.quark.ingest.rtmp;

import java.io.IOException;

import co.casterlabs.flv4j.actionscript.amf0.ECMAArray0;
import co.casterlabs.flv4j.actionscript.amf0.String0;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.script.FLVScriptTagData;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessage;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageAudio;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageData0;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageVideo;
import co.casterlabs.flv4j.rtmp.net.NetStatus;
import co.casterlabs.flv4j.rtmp.net.rpc.RPCHandler.MessageHandler;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionProvider;

class _RTMPSessionProvider implements SessionProvider, MessageHandler {
    private long dtsOffset;

    private final _RTMPConnection rtmp;
    private Session session;

    private boolean jammed = false;

    _RTMPSessionProvider(_RTMPConnection rtmp) {
        this.rtmp = rtmp;
    }

    void publish(String key, String type) throws IOException, InterruptedException {
        if (this.rtmp.state != _RTMPState.AUTHENTICATING) {
            this.rtmp.logger.debug("Closing, client sent publish() during state %s", this.rtmp.state);
            this.rtmp.stream.setStatus(NetStatus.NS_PUBLISH_FAILED);
            this.rtmp.close(true);
            return;
        }

        this.rtmp.logger.debug("Authenticating with %s @ %s", key, this.rtmp.handshakeUrl);
        this.session = Quark.authenticateSession(
            this,
            this.rtmp.conn.socket().getInetAddress().getHostAddress(),
            this.rtmp.handshakeUrl,
            this.rtmp.app,
            key
        );

        if (this.session == null) {
            this.rtmp.logger.debug("Closing, stream rejected.");
            this.rtmp.stream.setStatus(NetStatus.NS_PUBLISH_BADNAME);
            this.rtmp.close(true);
        } else {
            this.rtmp.logger.debug("Stream allowed.");
            this.rtmp.state = _RTMPState.PROVIDING;

            this.dtsOffset = session.prevDts;

            this.rtmp.stream.onMessage = this;
            this.rtmp.stream.setStatus(NetStatus.NS_PUBLISH_START);
        }
    }

    @Override
    public void onMessage(int timestamp, RTMPMessage message) {
        if (this.jammed) return; // Just in case.

        if (message instanceof RTMPMessageAudio audio) {
            this.handleAudio(timestamp, audio);
        } else if (message instanceof RTMPMessageVideo video) {
            this.handleVideo(timestamp, video);
        } else if (message instanceof RTMPMessageData0 data) {
            if (data.arguments().size() != 3) {
                return;
            }
            if (data.arguments().get(0) instanceof String0 str) {
                if (str.value().equals("@setDataFrame")) {
                    String0 method = (String0) data.arguments().get(1);
                    ECMAArray0 value = (ECMAArray0) data.arguments().get(2);

                    FLVScriptTagData payload = new FLVScriptTagData(method.value(), value);
                    FLVTag tag = new FLVTag(FLVTagType.SCRIPT, timestamp, 0, payload);
                    this.rtmp.logger.debug("Got script sequence: %s", tag);
                    this.session.tag(tag);
                }
            }
            return;
        } else {
            this.rtmp.logger.trace("Unhandled packet: %s", message);
        }
    }

    private void handleAudio(int timestamp, RTMPMessageAudio message) {
        if (this.session == null || this.rtmp.state != _RTMPState.PROVIDING) {
            this.rtmp.logger.debug("Closing, client sent tag during state %s", this.rtmp.state);
            this.rtmp.close(true);
            return;
        }

        long dts = timestamp + dtsOffset;

//         this.logger.trace("Audio packet: %s", read);
        FLVTag tag = new FLVTag(FLVTagType.AUDIO, dts, 0, message.payload());
        this.session.tag(tag);
    }

    private void handleVideo(int timestamp, RTMPMessageVideo message) {
        if (this.session == null || this.rtmp.state != _RTMPState.PROVIDING) {
            this.rtmp.logger.debug("Closing, client sent tag during state %s", this.rtmp.state);
            this.rtmp.close(true);
            return;
        }

        long dts = timestamp + dtsOffset;

        // this.logger.trace("Video packet: %s", read);
        FLVTag tag = new FLVTag(FLVTagType.VIDEO, dts, 0, message.payload());
        this.session.tag(tag);
    }

    void closeConnection(boolean graceful) {
        if (this.session == null) return;

        this.rtmp.stream.setStatus(NetStatus.NS_UNPUBLISH_SUCCESS);

        if (this.session != null && !this.jammed) {
            try {
                this.session.close(graceful);
            } catch (Throwable t) {
                this.rtmp.logger.warn("Exception whilst ending session, this could be bad!\n%s", t);
            }
        }
    }

    /* ---------------- */
    /*  Quark Session   */
    /* ---------------- */

    @Override
    public void close(boolean graceful) {
        this.rtmp.close(graceful);
    }

    @Override
    public void jam() {
        this.jammed = true;
        this.rtmp.logger.debug("Jammed!");
        this.rtmp.close(true);
    }

}
