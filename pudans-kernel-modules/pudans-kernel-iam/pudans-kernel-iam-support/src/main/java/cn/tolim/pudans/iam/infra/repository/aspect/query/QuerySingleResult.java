package cn.tolim.pudans.iam.infra.repository.aspect.query;

import java.lang.annotation.*;

@Documented
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface QuerySingleResult {
}
