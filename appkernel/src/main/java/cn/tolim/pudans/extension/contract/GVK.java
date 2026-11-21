package cn.tolim.pudans.extension.contract;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GVK {


    String group();


    String version();


    String kind();


    String plural();


    String singular();
}
