package co.casterlabs.quark.core.session;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.flv4j.flv.tags.FLVTag;
import co.casterlabs.quark.core.Quark;
import co.casterlabs.rakurai.json.element.JsonObject;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public abstract class SessionListener {
    public final String id;
    public final long createdAt = System.currentTimeMillis();

    private final List<Closeable> resources = new ArrayList<>();

    public SessionListener() {
        this(UUID.randomUUID().toString());
    }

    SessionListener(String id) {
        this.id = id;
    }

    public void onSequence(Session session, FLVSequence seq) {}

    public void onTag(Session session, FLVTag tag) {}

    protected void onClose0(Session session) {
        if (this.resources.isEmpty()) {
            FastLogger.logStatic(
                LogLevel.WARNING,
                "SessionListener %s has no tracked resources and no onClose0() overrides. "
                    + "This is likely a bug that will lead to resource exhaustion. "
                    + "To silence this warning, either properly track resources or override onClose0() with a NOOP.",
                this.getClass().getName()
            );
        }
    }

    /**
     * @return null, if internal.
     */
    public abstract @Nullable String type();

    public @Nullable String fid() {
        return null;
    }

    public @Nullable JsonObject metadata() {
        return null;
    }

    public final void onClose(Session session) {
        for (Closeable c : this.resources) {
            try {
                c.close();
            } catch (Throwable t) {
                if (Quark.DEBUG) {
                    t.printStackTrace();
                }
            }
        }

        this.onClose0(session);
    }

    protected void trackResource(Closeable resource) {
        this.resources.add(resource);
    }

}
