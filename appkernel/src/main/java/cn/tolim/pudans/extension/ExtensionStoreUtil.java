package cn.tolim.pudans.extension;

import cn.tolim.pudans.extension.contract.Scheme;
import org.springframework.util.StringUtils;


public final class ExtensionStoreUtil {

    private ExtensionStoreUtil() {}


    // 按组与复数名构造存储键前缀。
    public static String buildStoreNamePrefix(Scheme scheme) {

        StringBuilder builder = new StringBuilder("/registry/");
        if (StringUtils.hasText(scheme.groupVersionKind().group())) {
            builder.append(scheme.groupVersionKind().group()).append('/');
        }
        builder.append(scheme.plural());
        return builder.toString();
    }


    // 拼出带对象名称的完整存储键。
    public static String buildStoreName(Scheme scheme, String name) {
        return buildStoreNamePrefix(scheme) + "/" + name;
    }
}
