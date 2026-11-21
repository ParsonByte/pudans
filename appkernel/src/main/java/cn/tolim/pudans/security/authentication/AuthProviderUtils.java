package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.security.api.AuthProvider;
import cn.tolim.pudans.extension.api.MetadataUtil;
import org.apache.commons.lang3.BooleanUtils;

public final class AuthProviderUtils {

    private AuthProviderUtils() {}

    public static boolean isPrivileged(AuthProvider authProvider) {
        return BooleanUtils.TRUE.equals(
                MetadataUtil.nullSafeLabels(authProvider).get(AuthProvider.PRIVILEGED_LABEL));
    }
}
