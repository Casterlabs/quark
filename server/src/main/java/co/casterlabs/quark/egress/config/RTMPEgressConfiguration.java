package co.casterlabs.quark.egress.config;

import java.io.IOException;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.quark.egress.FFmpegRTMPSessionListener;
import co.casterlabs.quark.egress.RTMPSessionListener;
import co.casterlabs.quark.session.Session;
import co.casterlabs.quark.session.listeners.StreamFilter;
import co.casterlabs.quark.util.DependencyException;
import co.casterlabs.quark.util.FF;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import co.casterlabs.rakurai.json.validation.JsonValidate;

@JsonClass(exposeAll = true)
public class RTMPEgressConfiguration implements EgressConfiguration {
    public @Nullable String foreignId = null;
    public String url = null;
    public boolean useNativeImpl = false;
    public StreamFilter filter = StreamFilter.ALL_AUDIO;

    @JsonValidate
    private void $validate() {
        if (this.url == null) throw new IllegalArgumentException("url cannot be null.");
    }

    @Override
    public void create(Session session) throws IOException, DependencyException {
        if (this.useNativeImpl) {
            int lastSlash = this.url.lastIndexOf('/');
            String urlStrippedOfKey = this.url.substring(0, lastSlash);
            String key = this.url.substring(lastSlash + 1);

            session.addAsyncListener(new RTMPSessionListener(session, this.filter, this.foreignId, urlStrippedOfKey, key));
        } else {
            if (!FF.canUseMpeg) {
                throw new DependencyException("FFmpeg is not enabled.");
            }

            session.addAsyncListener(new FFmpegRTMPSessionListener(this.filter, this.url, this.foreignId));
        }
    }

}
