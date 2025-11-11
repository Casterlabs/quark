package co.casterlabs.quark.core.extensibility;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import co.casterlabs.rhs.protocol.api.endpoints.EndpointProvider;

/**
 * @implSpec Annotated type must implement {@link EndpointProvider}.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface QuarkHttpEndpoint {

}
