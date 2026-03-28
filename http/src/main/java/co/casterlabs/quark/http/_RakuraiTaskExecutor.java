package co.casterlabs.quark.http;

import java.util.concurrent.ThreadFactory;

import co.casterlabs.quark.core.Threads;
import co.casterlabs.rhs.util.TaskExecutor;

class _RakuraiTaskExecutor implements TaskExecutor {
    public static final _RakuraiTaskExecutor INSTANCE = new _RakuraiTaskExecutor();

    private static final ThreadFactory THREAD_FACTORY = Threads.lightIo("HTTP Task Pool");

    @Override
    public Task execute(Runnable toRun) {
        return new Task() {
            private final Thread thread;

            {
                this.thread = THREAD_FACTORY.newThread(toRun);
                this.thread.start();
            }

            @Override
            public void interrupt() {
                this.thread.interrupt();
            }

            @Override
            public void waitFor() throws InterruptedException {
                this.thread.join();
            }

            @Override
            public boolean isAlive() {
                return this.thread.isAlive();
            }
        };
    }

}
