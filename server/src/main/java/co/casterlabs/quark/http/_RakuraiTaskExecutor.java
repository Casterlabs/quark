package co.casterlabs.quark.http;

import co.casterlabs.rhs.util.TaskExecutor;

class _RakuraiTaskExecutor implements TaskExecutor {
    public static final _RakuraiTaskExecutor INSTANCE = new _RakuraiTaskExecutor();

    private static final Thread.Builder THREAD_FACTORY = Thread.ofVirtual().name("Http Task Pool - #", 0);

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
