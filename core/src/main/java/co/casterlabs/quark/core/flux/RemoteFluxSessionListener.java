package co.casterlabs.quark.core.flux;

import java.io.IOException;

import co.casterlabs.flux.client.realtime.FluxTubeListener;
import co.casterlabs.flv4j.actionscript.io.ASReader;
import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.QuarkSession;
import co.casterlabs.quark.core.util.FLVSequenceTag;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class RemoteFluxSessionListener implements QuarkSession.SessionListener, FluxTubeListener {
    private static final byte[] EMPTY = new byte[0];

    private final QuarkSession session;
    private final FastLogger logger;

    public RemoteFluxSessionListener(QuarkSession session) {
        this.session = session;
        this.logger = new FastLogger("Flux Interconnect (remote) @ " + session.id);

        FluxConnection.client.subscribe(Quark.FLUX_PREFIX + ":" + this.session.id, this);
    }

    @Override
    public void onSequenceRequest(QuarkSession session) {
        try {
            FluxConnection.publish(Quark.FLUX_PREFIX + ":" + this.session.id, QuarkPacketType.SEQ_REQUEST, EMPTY);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClose(QuarkSession _unused) {
        FluxConnection.client.unsubscribe(Quark.FLUX_PREFIX + ":" + this.session.id);

        try {
            FluxConnection.publish(Quark.FLUX_PREFIX + ":" + this.session.id, QuarkPacketType.END, EMPTY);
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    @Override
    public void onBinaryMessage(String from, byte[] message) {
        try {
            QuarkPacketType type = QuarkPacketType.LUT[message[0] & 0xFF];
            if (type == null) {
                this.logger.warn("Unrecognized packet type: %d", message[0] & 0xFF);
                return;
            }

            byte[] data = new byte[message.length - 1];
            System.arraycopy(message, 1, data, 0, data.length);

            switch (type) {
                case END:
                    this.logger.info("Closing session");
                    this.session.close();
                    break;

                case FLV_SEQUENCE: {
                    FLVTag tag = FLVTag.parse(new ASReader(data));
                    this.session.data(new FLVSequenceTag(tag));
                    break;
                }

                case FLV_TAG: {
                    FLVTag tag = FLVTag.parse(new ASReader(data));
                    this.session.data(tag);
                    break;
                }

                case SEQ_REQUEST:
                    break;
            }
        } catch (Throwable t) {
            this.logger.severe("Exception whilst processing packet:\n%s", t);
        }
    }

}
