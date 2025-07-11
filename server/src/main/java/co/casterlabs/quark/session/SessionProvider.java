package co.casterlabs.quark.session;

import co.casterlabs.rakurai.json.element.JsonObject;

public interface SessionProvider {

    public JsonObject metadata();

    public void jam();

    public void close(boolean wasGraceful);

}
