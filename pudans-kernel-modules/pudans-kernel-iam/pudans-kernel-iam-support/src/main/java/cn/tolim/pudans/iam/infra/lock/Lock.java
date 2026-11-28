package cn.tolim.pudans.iam.infra.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Lock {
    TimeUnit unit() default TimeUnit.SECONDS;

    long time() default 1800L;

    boolean throwException() default true;

    String key() default "";

    String namespaces() default "";
}
