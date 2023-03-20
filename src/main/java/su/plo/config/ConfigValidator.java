package su.plo.config;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Predicate;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(value = RUNTIME)
@Target(value = FIELD)
public @interface ConfigValidator {

    Class<? extends Predicate<? extends Object>> value();

    String[] allowed();
}
