package co.casterlabs.quark.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public record SocketConnection(
    Socket socket,
    InputStream in,
    OutputStream out
) implements Closeable {

    public SocketConnection(Socket socket) throws IOException {
        this(
            socket,
            socket.getInputStream(),
            socket.getOutputStream()
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
