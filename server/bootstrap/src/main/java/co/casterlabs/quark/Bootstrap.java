package co.casterlabs.quark;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Function;

import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import co.casterlabs.quark.core.Quark;
import co.casterlabs.quark.core.egress.config.EgressConfiguration;
import co.casterlabs.quark.core.extensibility.QuarkEgressConfiguration;
import co.casterlabs.quark.core.extensibility.QuarkEntrypoint;
import co.casterlabs.quark.core.extensibility.QuarkHttpEndpoint;
import co.casterlabs.quark.core.extensibility.QuarkStaticSessionListener;
import co.casterlabs.quark.core.extensibility._Extensibility;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionListener;
import co.casterlabs.quark.core.util.FF;
import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Bootstrap {

    /* ffplay -x 1280 -y 720 -volume 50 http://localhost:8080/session/test/egress/playback/flv */
    /* ffmpeg -stream_loop -1 -re -v debug -i test.flv -c copy -f flv rtmp://localhost/live/test */
    @SuppressWarnings({
            "unchecked",
            "deprecation"
    })
    public static void main(String[] args) throws Throwable {
        Quark.init();
        FF.init();

        try {
            Reflections reflections = new Reflections(
                new ConfigurationBuilder()
                    .setUrls(ClasspathHelper.forJavaClassPath())
            );

            for (Class<?> clazz : reflections.getTypesAnnotatedWith(QuarkEgressConfiguration.class)) {
                String name = clazz.getAnnotation(QuarkEgressConfiguration.class).value();
                FastLogger.logStatic(LogLevel.DEBUG, "Registering egress configuration: %s -> %s", name, clazz.getName());

                _Extensibility.egressConfigurations.put(name, (Class<? extends EgressConfiguration>) clazz);
            }

            for (Class<?> clazz : reflections.getTypesAnnotatedWith(QuarkHttpEndpoint.class)) {
                EndpointProvider provider = (EndpointProvider) clazz.newInstance();
                FastLogger.logStatic(LogLevel.DEBUG, "Registering HTTP endpoint provider: %s", clazz.getName());

                _Extensibility.http.register(provider);
            }

            for (Class<?> clazz : reflections.getTypesAnnotatedWith(QuarkStaticSessionListener.class)) {
                QuarkStaticSessionListener annotation = clazz.getAnnotation(QuarkStaticSessionListener.class);
                FastLogger.logStatic(LogLevel.DEBUG, "Registering static session listener: %s (async=%s)", clazz.getName(), annotation.async());

                Function<Session, SessionListener> factory = (Function<Session, SessionListener>) clazz.getField("FACTORY").get(null);

                if (annotation.async()) {
                    _Extensibility.asyncStaticSessionListeners.add(factory);
                } else {
                    _Extensibility.syncStaticSessionListeners.add(factory);
                }
            }

            for (Class<?> clazz : reflections.getTypesAnnotatedWith(QuarkEntrypoint.class)) {
                FastLogger.logStatic(LogLevel.DEBUG, "Starting entrypoint: %s", clazz.getName());
                clazz.getMethod("start").invoke(null);
            }

            reflections = null;
            System.gc();
            System.gc();
            System.gc();

            FastLogger.logStatic(LogLevel.DEBUG, "Quark bootstrap complete.");
        } catch (InvocationTargetException e) {
            throw e.getTargetException(); // Unwrap.
        }
    }

}
