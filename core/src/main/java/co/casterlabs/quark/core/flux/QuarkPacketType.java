package co.casterlabs.quark.core.flux;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
enum QuarkPacketType {
    // Control
    END(0x00),
    SEQ_REQUEST(0x01),

    // Payloads
    FLV_SEQUENCE(0x10),
    FLV_TAG(0x11),

    ;

    public static final QuarkPacketType[] LUT = new QuarkPacketType[256];
    static {
        for (QuarkPacketType protocol : values()) {
            LUT[protocol.id] = protocol;
        }
    }

    public final int id; // u8

}
