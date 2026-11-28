package cn.tolim.pudans.iam.infra.web.converter;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EnumConvert {
}
