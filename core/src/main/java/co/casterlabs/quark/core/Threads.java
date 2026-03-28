package co.casterlabs.quark.core;

import java.util.concurrent.ThreadFactory;

public class Threads {
    public static final Thread.Builder HEAVY_IO_THREAD_BUILDER = Quark.EXP_VIRTUAL_THREAD_HEAVY_IO ? Thread.ofVirtual() : Thread.ofPlatform();
    public static final Thread.Builder LIGHT_IO_THREAD_BUILDER = Thread.ofVirtual();
    public static final Thread.Builder MISC_THREAD_BUILDER = Thread.ofVirtual();

    public static ThreadFactory heavyIo(String name) {
        return HEAVY_IO_THREAD_BUILDER
            .name("HIO - " + name, 0)
            .factory();
    }

    public static ThreadFactory lightIo(String name) {
        return LIGHT_IO_THREAD_BUILDER
            .name("LIO - " + name, 0)
            .factory();
    }

    public static ThreadFactory misc(String name) {
        return MISC_THREAD_BUILDER
            .name("MISC - " + name, 0)
            .factory();
    }

}
