package co.casterlabs.quark.ingest.rtmp;

import java.io.IOException;
import java.util.Map;

import co.casterlabs.flv4j.actionscript.amf0.AMF0Type;
import co.casterlabs.flv4j.actionscript.amf0.AMF0Type.ObjectLike;
import co.casterlabs.flv4j.actionscript.amf0.AMF0Type.StringLike;
import co.casterlabs.flv4j.actionscript.amf0.Boolean0;
import co.casterlabs.flv4j.actionscript.amf0.Date0;
import co.casterlabs.flv4j.actionscript.amf0.ECMAArray0;
import co.casterlabs.flv4j.actionscript.amf0.Number0;
import co.casterlabs.flv4j.actionscript.amf0.Object0;
import co.casterlabs.flv4j.actionscript.amf0.StrictArray0;
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
import co.casterlabs.quark.Sessions;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.SessionProvider;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonBoolean;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonNull;
import co.casterlabs.rakurai.json.element.JsonNumber;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.element.JsonString;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

class _RTMPSessionProvider implements SessionProvider, MessageHandler {
    private long dtsOffset;

    private final _RTMPConnection rtmp;
    private Session session;

    private boolean jammed = false;

    private JsonObject metadata = JsonObject.EMPTY_OBJECT;

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

        String app = this.rtmp.connectArgs.app();
        String handshakeUrl = this.rtmp.connectArgs.tcUrl();

        this.rtmp.logger.debug("Authenticating with %s @ %s", key, handshakeUrl);
        this.session = Sessions.authenticateSession(
            this,
            this.rtmp.conn.socket().getInetAddress().getHostAddress(),
            handshakeUrl,
            app,
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

            JsonElement connectAsJson = amfToJson(
                new Object0(
                    this.rtmp.connectArgs.additionalParams()
                )
            );

            this.metadata = new JsonObject()
                .put("type", "RTMP")
                .put("key", key)
                .put("app", app)
                .put("publishType", type)
                .put("handshakeUrl", handshakeUrl)
                .put("connectArgs", connectAsJson)
                .put("dtsOffset", this.dtsOffset);
            this.rtmp.logger.debug("Metadata: %s", this.metadata);

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
            if (data.arguments().size() == 3 &&
                data.arguments().get(0) instanceof String0 str &&
                str.value().equals("@setDataFrame")) {
                String0 method = (String0) data.arguments().get(1);
                ObjectLike value = (ObjectLike) data.arguments().get(2);

                FLVScriptTagData payload = new FLVScriptTagData(method.value(), new ECMAArray0(value.map()));
                FLVTag tag = new FLVTag(FLVTagType.SCRIPT, timestamp, 0, payload);
                this.rtmp.logger.debug("Got script sequence: %s", tag);
                this.session.tag(tag);
            } else {
                this.rtmp.logger.debug("Unknown message: %s(%s)", data, data.arguments());
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

//        this.rtmp.logger.trace("Audio packet: %s", message);
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

//        this.rtmp.logger.trace("Video packet: %s", message);
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
    public JsonObject metadata() {
        return this.metadata;
    }

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

    private static JsonElement amfToJson(AMF0Type type) {
        try {
            switch (type.type()) {
                case BOOLEAN:
                    return new JsonBoolean(((Boolean0) type).value());

                case NUMBER:
                    return new JsonNumber(((Number0) type).value());

                case DATE:
                    return new JsonNumber(((Date0) type).value());

                case TYPED_OBJECT:
                case ECMA_ARRAY:
                case OBJECT: {
                    ObjectLike obj = (ObjectLike) type;
                    JsonObject json = new JsonObject();
                    for (Map.Entry<String, AMF0Type> entry : obj.map().entrySet()) {
                        json.put(entry.getKey(), amfToJson(entry.getValue()));
                    }
                    return json;
                }

                case STRICT_ARRAY: {
                    StrictArray0 arr = (StrictArray0) type;
                    JsonArray json = new JsonArray();
                    for (AMF0Type e : arr.array()) {
                        json.add(amfToJson(e));
                    }
                    return json;
                }

                case LONG_STRING:
                case STRING:
                case XML_DOCUMENT:
                    return new JsonString(((StringLike) type).value());

                case SWITCH_TO_AMF3:
                case NULL:
                case OBJECT_END:
                case UNDEFINED:
                case UNSUPPORTED:
                case REFERENCE:
                case RESERVED_14:
                case RESERVED_4:
                default:
                    return JsonNull.INSTANCE;
            }
        } catch (Throwable t) {
            // In case I messed something up...
            FastLogger.logStatic(LogLevel.WARNING, "An error occurred while converting AMF0 (%s) to JSON:\n%s", type, t);
            return JsonNull.INSTANCE;
        }
    }

}
