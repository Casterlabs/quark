package co.casterlabs.quark.core.extensibility;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * @implSpec Annotated type must have a static FACTORY
 *           {@literal Function<Session, SessionListener>} field.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface QuarkStaticSessionListener {

    boolean async() default false;

    /**
     * @return whether or not this static listener requires stream info to be known
     *         before the factory is called.
     */
    boolean requiresInfo() default false;

}
