package co.casterlabs.quark.core.session;

import co.casterlabs.rakurai.json.element.JsonObject;

public interface SessionProvider {

    public JsonObject metadata();

    public void jam();

    public void close(boolean wasGraceful);

}
