package co.casterlabs.quark.http;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.rhs.util.TaskExecutor;

class _RakuraiTaskExecutor implements TaskExecutor {
    public static final _RakuraiTaskExecutor INSTANCE = new _RakuraiTaskExecutor();

    // Temporary until we can fully resolve pinning issues.
    // Should normally be ofVirtual.
    private static final Thread.Builder THREAD_FACTORY = Quark.HEAVY_IO_THREAD_BUILDER.name("Http Task Pool - #", 0);

    @Override
    public Task execute(Runnable toRun) {
        return new Task() {
            private final Thread thread = THREAD_FACTORY.start(toRun);

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
