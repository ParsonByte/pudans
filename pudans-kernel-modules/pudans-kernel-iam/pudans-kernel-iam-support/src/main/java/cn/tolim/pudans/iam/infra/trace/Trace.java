package cn.tolim.pudans.iam.infra.trace;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Trace {
}
