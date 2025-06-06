package co.casterlabs.quark.util;

import java.util.function.Consumer;
import java.util.function.Function;

import lombok.Locked;
import lombok.NonNull;

public class ModifiableArray<T> {
    private final Function<Integer, T[]> arrayProvider;
    private volatile T[] items;

    public ModifiableArray(@NonNull Function<Integer, T[]> arrayProvider) {
        this.arrayProvider = arrayProvider;
        this.items = arrayProvider.apply(0);
    }

    @Locked
    public void add(T itemToAdd) {
        T[] newArr = this.arrayProvider.apply(this.items.length + 1);
        System.arraycopy(this.items, 0, newArr, 0, this.items.length);
        newArr[this.items.length] = itemToAdd;

        this.items = newArr;
    }

    @Locked
    public void remove(T itemToRemove) {
        int occurrences = 0;
        for (T i : this.items) {
            if (i == itemToRemove) {
                occurrences++;
            }
        }
        if (occurrences == 0) return;

        T[] newArr = this.arrayProvider.apply(this.items.length - occurrences);
        int newArrIdx = 0;
        for (T i : this.items) {
            if (i != itemToRemove) {
                newArr[newArrIdx] = i;
                newArrIdx++;
            }
        }

        this.items = newArr;
    }

    public T[] get() {
        return this.items;
    }

    public int length() {
        return this.items.length;
    }

    public void forEach(Consumer<T> consumer) {
        T[] items = this.items;
        for (T i : items) {
            try {
                consumer.accept(i);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

}
