package co.casterlabs.quark.egress.http;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import co.casterlabs.commons.io.streams.StreamUtil;
import co.casterlabs.quark.session.QuarkSession;
import co.casterlabs.quark.session.QuarkSessionListener;
import co.casterlabs.quark.session.listeners.FLVProcessSessionListener;
import co.casterlabs.rhs.protocol.http.HttpResponse.ResponseContent;

class _RemuxedResponseContent implements ResponseContent {
    private final QuarkSession qSession;
    private final String[] command;

    _RemuxedResponseContent(QuarkSession qSession, String... command) {
        this.qSession = qSession;
        this.command = command;
    }

    @Override
    public void write(int recommendedBufferSize, OutputStream out) throws IOException {
        CompletableFuture<Void> waitFor = new CompletableFuture<>();

        QuarkSessionListener listener = new FLVProcessSessionListener(
            Redirect.PIPE, Redirect.INHERIT,
            this.command
        ) {

            {
                Thread.ofVirtual().name("FFMpeg -> HTTP", 0)
                    .start(() -> {
                        try {
                            StreamUtil.streamTransfer(this.stdout(), out, 8192);
                        } catch (IOException e) {} finally {
                            waitFor.complete(null);
                        }
                    });
            }

            @Override
            public void onClose(QuarkSession session) {
                super.onClose(session);
                waitFor.complete(null);
            }
        };

        try {
            qSession.addListener(listener);
            waitFor.get();
        } catch (InterruptedException | ExecutionException ignored) {
            // NOOP
        } finally {
            qSession.removeListener(listener);
        }
    }

    @Override
    public long length() {
        return Long.MAX_VALUE; // infinite length. causes browsers to never seek, more efficient than chunked.
    }

    @Override
    public void close() throws IOException {
        // NOOP
    }
}
