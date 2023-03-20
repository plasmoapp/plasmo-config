package su.plo.config;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Function;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(value = RUNTIME)
@Target(value = {FIELD, TYPE})
public @interface ConfigFieldProcessor {

    Class<? extends Function<?, ?>>[] value();
}
