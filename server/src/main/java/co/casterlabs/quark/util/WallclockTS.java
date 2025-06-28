package co.casterlabs.quark.util;

public class WallclockTS {
    private volatile long base = System.currentTimeMillis();
    private volatile long prevTimestamp = 0L;

    public void offset(long offset) {
        this.base += offset;
    }

    public long next() {
        long now = System.currentTimeMillis() - this.base;

        if (now <= this.prevTimestamp) {
            now = this.prevTimestamp + 1; // force it to be monotonic.
        }

        now &= 0xFFFFFFFFL; // as unsigned 32b integer

        this.prevTimestamp = now;
        return now;
    }

}
