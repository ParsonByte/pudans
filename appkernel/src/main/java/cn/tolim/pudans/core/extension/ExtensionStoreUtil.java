package cn.tolim.pudans.core.extension;

import cn.tolim.pudans.contract.extension.Scheme;
import org.springframework.util.StringUtils;


public final class ExtensionStoreUtil {

    private ExtensionStoreUtil() {}


    public static String buildStoreNamePrefix(Scheme scheme) {

        StringBuilder builder = new StringBuilder("/registry/");
        if (StringUtils.hasText(scheme.groupVersionKind().group())) {
            builder.append(scheme.groupVersionKind().group()).append('/');
        }
        builder.append(scheme.plural());
        return builder.toString();
    }


    public static String buildStoreName(Scheme scheme, String name) {
        return buildStoreNamePrefix(scheme) + "/" + name;
    }
}
