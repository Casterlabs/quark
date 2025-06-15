package co.casterlabs.quark.ingest.protocols.rtmp;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import co.casterlabs.flv4j.EndOfStreamException;
import co.casterlabs.flv4j.actionscript.amf0.AMF0Type;
import co.casterlabs.flv4j.actionscript.amf0.ECMAArray0;
import co.casterlabs.flv4j.actionscript.amf0.LongString0;
import co.casterlabs.flv4j.actionscript.amf0.Null0;
import co.casterlabs.flv4j.actionscript.amf0.Object0;
import co.casterlabs.flv4j.actionscript.amf0.String0;
import co.casterlabs.flv4j.actionscript.io.ASReader;
import co.casterlabs.flv4j.actionscript.io.ASWriter;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.flv4j.flv.tags.FLVTagType;
import co.casterlabs.flv4j.flv.tags.script.FLVScriptTagData;
import co.casterlabs.flv4j.rtmp.RTMPReader;
import co.casterlabs.flv4j.rtmp.RTMPWriter;
import co.casterlabs.flv4j.rtmp.chunks.RTMPChunk;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageAudio;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageChunkSize;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageCommand0;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageData0;
import co.casterlabs.flv4j.rtmp.chunks.RTMPMessageVideo;
import co.casterlabs.flv4j.rtmp.handshake.RTMPHandshake1;
import co.casterlabs.flv4j.rtmp.handshake.RTMPHandshake2;
import co.casterlabs.quark.Quark;
import co.casterlabs.quark.session.FLVData;
import co.casterlabs.quark.session.FLVSequence;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.quark.session.QuarkSessionListener;
import co.casterlabs.quark.util.SocketConnection;
import co.casterlabs.quark.util.WallclockTS;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

class _RTMPConnection extends QuarkSessionListener implements AutoCloseable {
    private static final int CHUNK_SIZE = 4096;

    private static final String0 NETCONNECTION_CONNECT_SUCCESS = new String0("NetConnection.Connect.Success");
    private static final String0 NETCONNECTION_CONNECT_FAILED = new String0("NetConnection.Connect.Failed");

    private static final Object0 NETSTREAM_PUBLISH_START = new Object0(
        Map.of(
            "code", new String0("NetStream.Publish.Start"),
            "level", new String0("status")
        )
    );

    private static final Object0 NETSTREAM_PUBLISH_BADNAME = new Object0(
        Map.of(
            "code", new String0("NetStream.Publish.BadName"),
            "level", new String0("status")
        )
    );

    private static final Object0 NETSTREAM_PUBLISH_FAILED = new Object0(
        Map.of(
            "code", new String0("NetStream.Publish.Failed"),
            "level", new String0("status")
        )
    );

    private final SocketConnection conn;
    private final RTMPReader in;
    private final RTMPWriter out;

    private final FastLogger logger;

    private final List<FLVTag> sequenceTags = new LinkedList<>();

    private final WallclockTS dts = new WallclockTS();
    private long ptsOffset = 0;

    private State state = State.INITIALIZING;
    private QuarkSession session;
    private String handshakeUrl = null;
    private boolean jammed = false;

    _RTMPConnection(SocketConnection conn) throws IOException {
        this.conn = conn;
        this.in = new RTMPReader(new ASReader(conn.in()));
        this.out = new RTMPWriter(new ASWriter(conn.out()));
        this.logger = new FastLogger(conn.socket().toString());
    }

    /* ---------------- */
    /*       RTMP       */
    /* ---------------- */

    void run() {
        try {
            this.logger.trace("Performing handshake 0");
            this.in.handshake0(); // Consume. Should always be version 3.
            this.out.handshake0();

            this.logger.trace("Performing handshake 1");
            RTMPHandshake1 handshake1 = this.in.handshake1();
            this.out.handshake1();

            this.logger.trace("Performing handshake 2");
            this.out.handshake2(handshake1);
            RTMPHandshake2 handshake2 = this.in.handshake2();

            if (!this.out.validateHandshake2(handshake2)) {
                this.logger.debug("Closing, handshake failed.");
                this.close();
            }

            this.logger.trace("Handshake successful.");

            this.out.write(2, 0, 0, new RTMPMessageChunkSize(CHUNK_SIZE));

            while (true) {
                RTMPChunk<?> read = this.in.read();
                if (read == null) continue;

                this.handle(read);
            }
        } catch (EndOfStreamException e) {
            throw e;
        } catch (Throwable t) {
            if ("Socket closed".equals(t.getMessage())) {
                throw new EndOfStreamException(t);
            }
            if ("The pipe has been ended".equals(t.getMessage())) {
                throw new EndOfStreamException(t);
            }
            if ("An established connection was aborted by the software in your host machine".equals(t.getMessage())) {
                throw new EndOfStreamException(t);
            }

            this.logger.fatal("Unhandled exception, aborting...\n%s", t);
        }
    }

    private synchronized void rpcInvoke(RTMPChunk<RTMPMessageCommand0> chunk, String commandName, AMF0Type... arguments) throws IOException {
        this.logger.trace("Sending RPC: %s(%s)", commandName, arguments);
        this.out.write(
            chunk.chunkStreamId(),
            chunk.messageStreamId(),
            0,
            new RTMPMessageCommand0(
                new String0(commandName),
                chunk.message().transactionId(),
                Arrays.asList(arguments)
            )
        );
    }

    @SuppressWarnings("unchecked")
    private void handle(RTMPChunk<?> read) throws IOException {
        if (read.message() instanceof RTMPMessageCommand0) {
            this.handleCommand((RTMPChunk<RTMPMessageCommand0>) read);
        } else if (read.message() instanceof RTMPMessageAudio) {
            this.handleAudio((RTMPChunk<RTMPMessageAudio>) read);
        } else if (read.message() instanceof RTMPMessageVideo) {
            this.handleVideo((RTMPChunk<RTMPMessageVideo>) read);
        } else if (read.message() instanceof RTMPMessageData0 data) {
            if (this.jammed) return; // Just in case.
            if (data.arguments().size() != 3) {
                return;
            }
            if (data.arguments().get(0) instanceof String0 str) {
                if (str.value().equals("@setDataFrame")) {
                    String0 method = (String0) data.arguments().get(1);
                    ECMAArray0 value = (ECMAArray0) data.arguments().get(2);

                    FLVScriptTagData payload = new FLVScriptTagData(method.value(), value);
                    FLVTag tag = new FLVTag(FLVTagType.SCRIPT, 0, (int) read.messageStreamId(), payload);
                    this.logger.debug("Got script sequence: %s", tag);
                    this.sequenceTags.add(tag);
                    this.session.sequence(new FLVSequence(tag)); // /shrug/
                }
            }
            return;
        } else {
            this.logger.trace("Unhandled packet: %s", read);
        }

    }

    private void handleCommand(RTMPChunk<RTMPMessageCommand0> read) throws IOException {
        this.logger.trace("Command packet: %s", read);

        switch (read.message().commandName().value()) {
            case "connect": {
                if (this.state != State.INITIALIZING) {
                    this.logger.debug("Closing, client sent connect() during state %s", this.state);
                    this.rpcInvoke(read, "_result", NETCONNECTION_CONNECT_FAILED);
                    this.close();
                    return;
                }

                if (!read.message().arguments().isEmpty()) {
                    AMF0Type first = read.message().arguments().getFirst();

                    Map<String, AMF0Type> map = null;
                    if (first instanceof ECMAArray0 obj) {
                        map = obj.map();
                    } else if (first instanceof Object0 obj) {
                        map = obj.map();
                    }

                    if (map != null) {
                        AMF0Type tcUrl0 = map.get("tcUrl");
                        if (tcUrl0 != null) {
                            if (tcUrl0 instanceof String0 str) {
                                this.handshakeUrl = str.value();
                            }
                            if (tcUrl0 instanceof LongString0 str) {
                                this.handshakeUrl = str.value();
                            }
                        }
                    }
                }

                if (this.handshakeUrl == null) {
                    // No url, reject.
                    this.logger.debug("Closing, no tcUrl.");
                    this.rpcInvoke(read, "_result", NETCONNECTION_CONNECT_FAILED);
                } else {
                    // "Allow" the url as long as it's present, we'll validate it during publish().
                    this.state = State.AUTHENTICATING;
                    this.rpcInvoke(read, "_result", NETCONNECTION_CONNECT_SUCCESS);
                }
                return;
            }

            case "publish":
                if (this.state != State.AUTHENTICATING) {
                    this.logger.debug("Closing, client sent publish() during state %s", this.state);
                    this.rpcInvoke(
                        read, "onStatus",
                        Null0.INSTANCE, NETSTREAM_PUBLISH_BADNAME
                    );
                    this.close();
                    return;
                }

                String key = null;
                if (read.message().arguments().size() > 1) {
                    AMF0Type second = read.message().arguments().get(1);
                    if (second instanceof String0 str) {
                        key = str.value();
                    }
                    if (second instanceof LongString0 str) {
                        key = str.value();
                    }
                }

                this.logger.debug("Authenticating with %s @ %s", key, this.handshakeUrl);
                this.session = Quark.authenticateSession(this, this.handshakeUrl, key);
                this.dts.offset(-this.session.prevDts);
                this.ptsOffset = this.session.prevPts;

                if (this.session == null) {
                    this.logger.debug("Closing, stream rejected.");
                    this.rpcInvoke(
                        read, "onStatus",
                        Null0.INSTANCE, NETSTREAM_PUBLISH_FAILED
                    );
                    this.close();
                } else {
                    // Allow it!

                    this.logger.debug("Stream allowed.");
                    this.state = State.RUNNING;
                    this.rpcInvoke(
                        read, "onStatus",
                        Null0.INSTANCE, NETSTREAM_PUBLISH_START
                    );
                }
                return;

            case "deleteStream":
                this.logger.debug("Stream closed by client.");
                this.rpcInvoke(read, "_result", Null0.INSTANCE);
                this.close();
                return;

            case "releaseStream":
            case "createStream":
                // Dummy.
                this.rpcInvoke(read, "_result", Null0.INSTANCE);
                return;

            case "FCPublish":
            case "FCUnpublish":
                return; // Unhandled but recognized.

        }
    }

    private void handleAudio(RTMPChunk<RTMPMessageAudio> read) throws IOException {
        if (this.jammed) return; // Just in case.

//        this.logger.trace("Audio packet: %s", read);

        if (this.session == null || this.state != State.RUNNING) {
            this.logger.debug("Closing, client sent tag during state %s", this.state);
            this.close();
            return;
        }

        FLVTag tag = new FLVTag(FLVTagType.AUDIO, this.dts.next(), (int) read.messageStreamId(), read.message().payload());

        if (tag.data().isSequenceHeader()) {
            this.logger.debug("Got audio sequence: %s", tag);
            this.sequenceTags.add(tag);
            this.session.sequence(new FLVSequence(tag)); // /shrug/
        } else {
            this.session.data(new FLVData(read.timestamp() + this.ptsOffset, tag));
        }
    }

    private void handleVideo(RTMPChunk<RTMPMessageVideo> read) throws IOException {
        if (this.jammed) return; // Just in case.

//        this.logger.trace("Video packet: %s", read);

        if (this.session == null || this.state != State.RUNNING) {
            this.logger.debug("Closing, client sent tag during state %s", this.state);
            this.close();
            return;
        }

        FLVTag tag = new FLVTag(FLVTagType.VIDEO, this.dts.next(), (int) read.messageStreamId(), read.message().payload());

        if (tag.data().isSequenceHeader()) {
            this.logger.debug("Got video sequence: %s", tag);
            this.sequenceTags.add(tag);
            this.session.sequence(new FLVSequence(tag)); // /shrug/
        } else {
            this.session.data(new FLVData(read.timestamp() + this.ptsOffset, tag));
        }
    }

    @Override
    public void close() throws IOException {
        if (this.state == State.CLOSING) return;

        this.logger.debug("Closing...");
        this.state = State.CLOSING;

        if (this.session != null && !this.jammed) {
            try {
                this.session.close();
            } catch (Throwable t) {
                this.logger.warn("Exception whilst ending session, this could be bad!\n%s", t);
            }
        }

        this.conn.close();
        this.logger.debug("Closed!");
    }

    /* ---------------- */
    /*  Quark Session   */
    /* ---------------- */

    @Override
    public void onJam(QuarkSession session) {
        this.jammed = true;
        this.logger.debug("Jammed!");
        session.removeListener(this);
    }

    @Override
    public void onSequenceRequest(QuarkSession session) {
        session.sequence(new FLVSequence(this.sequenceTags.toArray(new FLVTag[0])));
    }

    @Override
    public void onClose(QuarkSession session) {
        try {
            this.close();
        } catch (IOException ignored) {}
    }

    @Override
    public boolean async() {
        return false;
    }

    /* ---------------- */
    /*      Misc.       */
    /* ---------------- */

    private static enum State {
        INITIALIZING,
        AUTHENTICATING,
        RUNNING,
        CLOSING,
    }

}
