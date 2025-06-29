package co.casterlabs.quark.util;

public class WallclockTS {
    private long base = -1;
    private long offset = System.currentTimeMillis();
    private long prevTimestamp = 0L;

    public void offset(long offset) {
        this.offset = offset;
    }

    public long next() {
        if (this.base == -1) {
            this.base = System.currentTimeMillis();
        }

        long now = System.currentTimeMillis() - this.base + offset;
        now &= 0xFFFFFFFFL; // as unsigned 32b integer

        if (now < this.prevTimestamp) {
            now = this.prevTimestamp; // force it to be monotonic.
        }

        this.prevTimestamp = now;
        return now;
    }

}
