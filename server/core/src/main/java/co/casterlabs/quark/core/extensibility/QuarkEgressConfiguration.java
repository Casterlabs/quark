package co.casterlabs.quark.core.extensibility;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import co.casterlabs.quark.core.egress.config.EgressConfiguration;

/**
 * @implSpec Annotated type must implement {@link EgressConfiguration}.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface QuarkEgressConfiguration {

    String value();

}
