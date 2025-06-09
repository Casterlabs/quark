package co.casterlabs.quark.util;

public class WallclockTS {
    private final long base = System.currentTimeMillis();
    private volatile long prevTimestamp = 0L;

    public long next() {
        long now = System.currentTimeMillis() - this.base;

        if (now <= this.prevTimestamp) {
            now = this.prevTimestamp + 1; // force it to be monotonic.
        }

        now &= 0xFFFFFFFF; // as unsigned 32b integer

        this.prevTimestamp = now;
        return now;
    }

}
