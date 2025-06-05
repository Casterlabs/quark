package co.casterlabs.quark.ingest.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import co.casterlabs.commons.io.streams.MTUOutputStream;

public record SocketConnection(
    Socket socket,
    InputStream in,
    OutputStream out
) implements Closeable {

    public SocketConnection(Socket socket) throws IOException {
        this(
            socket,
            socket.getInputStream(),
            new MTUOutputStream(
                socket.getOutputStream(),
                MTUOutputStream.guessMtu(socket)
            )
        );
    }

    @Override
    public void close() throws IOException {
        try {
            this.out.flush();
        } catch (IOException ignored) {}

        this.socket.close();
    }

}
