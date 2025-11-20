package co.casterlabs.quark.core.extensibility;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jetbrains.annotations.ApiStatus.Internal;

import co.casterlabs.quark.core.egress.config.EgressConfiguration;
import co.casterlabs.quark.core.session.Session;
import co.casterlabs.quark.core.session.SessionListener;
import co.casterlabs.rhs.protocol.api.ApiFramework;

/**
 * @apiNote Use the relevant annotations rather than modifying this class
 *          directly.
 */
@Internal
public class _Extensibility {

    public static final Map<String, Class<? extends EgressConfiguration>> egressConfigurations = new HashMap<>();

    public static final ApiFramework http = new ApiFramework();

    public static final List<Function<Session, SessionListener>> syncStaticSessionListeners = new LinkedList<>();
    public static final List<Function<Session, SessionListener>> asyncStaticSessionListeners = new LinkedList<>();

}
