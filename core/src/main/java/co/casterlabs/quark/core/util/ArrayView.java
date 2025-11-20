package co.casterlabs.quark.core.util;

import java.util.Arrays;

public record ArrayView(byte[] data, int offset, int length) {

    public static final ArrayView EMPTY = new ArrayView(new byte[0], 0, 0);

    public byte get(int index) {
        return this.data[this.offset + index];
    }

    public void set(int index, byte value) {
        this.data[this.offset + index] = value;
    }

    public byte[] toArray() {
        byte[] arr = new byte[this.length];
        System.arraycopy(this.data, this.offset, arr, 0, this.length);
        return arr;
    }

    @Override
    public final boolean equals(Object obj) {
        if (obj instanceof ArrayView other) {
            if (this == obj) return true;
            if (this.length != other.length) return false;

            return Arrays.equals(
                this.data, this.offset, this.offset + this.length,
                other.data, other.offset, other.offset + other.length
            );
        }

        return false;
    }

    @Override
    public int hashCode() {
        int result = 1;
        for (int i = offset; i < this.length; i++) {
            result = 31 * result + this.get(i);
        }
        return result;
    }

}
