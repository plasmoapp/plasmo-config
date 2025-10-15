package su.plo.config;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(value = RUNTIME)
@Target(value = TYPE)
public @interface Config {
    String comment() default "";

    /**
     * If set to true, fields without @ConfigField annotation will not be serialized.
     */
    boolean loadConfigFieldOnly() default false;
}
