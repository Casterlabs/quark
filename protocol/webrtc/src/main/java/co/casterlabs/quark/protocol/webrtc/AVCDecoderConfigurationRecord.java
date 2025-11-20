package co.casterlabs.quark.protocol.webrtc;

import co.casterlabs.quark.core.util.ArrayView;

public class AVCDecoderConfigurationRecord {

    public static byte[] from(ArrayView sps, ArrayView pps) {
        byte[] record = new byte[5 + 3 + sps.length() + 3 + pps.length()];

        record[0] = 1; // configurationVersion
        record[1] = sps.get(1); // profile
        record[2] = sps.get(2); // compatibility
        record[3] = sps.get(3); // level
        record[4] = (byte) 0xFF; // lengthSizeMinusOne = 3

        int spsStart = 5 + 3;
        record[5] = (byte) 0xE1; // numOfSPS = 1
        record[6] = (byte) ((sps.length() >> 8) & 0xFF);
        record[7] = (byte) (sps.length() & 0xFF);
        System.arraycopy(sps.data(), sps.offset(), record, spsStart, sps.length());

        int ppsStart = spsStart + sps.length();
        record[ppsStart] = 1; // numOfPPS = 1
        record[ppsStart + 1] = (byte) ((pps.length() >> 8) & 0xFF);
        record[ppsStart + 2] = (byte) (pps.length() & 0xFF);
        System.arraycopy(pps.data(), pps.offset(), record, ppsStart + 3, pps.length());

        return record;
    }

}
