package co.casterlabs.quark.core.util;

public class PortRange {
    private static final int BASE_PORT = 20000; // Arbitrary choice, range seems mostly dead/unused.
    private static final int MAX_PORTS = 05000;

    private static final boolean[] portInUse = new boolean[MAX_PORTS];

    public static synchronized int acquirePort() {
        for (int portIndex = 0; portIndex < MAX_PORTS; portIndex++) {
            if (!portInUse[portIndex]) {
                portInUse[portIndex] = true;
                return BASE_PORT + portIndex;
            }
        }
        throw new IllegalStateException("No ephermal ports available.");
    }

    public static synchronized void releasePort(int port) {
        int portIndex = port - BASE_PORT;
        portInUse[portIndex] = false;
    }

}
