package co.casterlabs.quark.protocol.pipeline.egress;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.core.egress.config.EgressConfiguration;
import co.casterlabs.quark.core.extensibility.QuarkEgressConfiguration;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.listeners.StreamFilter;
import co.casterlabs.quark.core.util.DependencyException;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.validation.JsonValidate;

@JsonClass(exposeAll = true)
@QuarkEgressConfiguration("pipeline")
public class PipelineEgressConfiguration implements EgressConfiguration {
    public @Nullable String foreignId = null;
    public @Nullable String resultId = null;

    public String[] command = null;
    public StreamFilter filter = StreamFilter.ALL_AUDIO;

    @JsonValidate
    private void $validate() {
        if (this.command == null) throw new IllegalArgumentException("command cannot be null.");
        for (String arg : this.command) {
            if (arg == null) throw new IllegalArgumentException("command arg cannot be null.");
        }
    }

    @Override
    public void create(Session session) throws IOException, DependencyException {
        session.addAsyncListener(new PipelineSessionListener(this.filter, this.foreignId, this.resultId, this.command));
    }

}
