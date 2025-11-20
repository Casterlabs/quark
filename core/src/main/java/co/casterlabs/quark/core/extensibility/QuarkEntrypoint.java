package co.casterlabs.quark.core.extensibility;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * @implSpec Annotated type must have a non-blocking static start() method.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface QuarkEntrypoint {

}
