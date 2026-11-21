package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.contract.security.resource.AuthProvider;
import cn.tolim.pudans.contract.extension.MetadataUtil;
import org.apache.commons.lang3.BooleanUtils;

public final class AuthProviderUtils {

    private AuthProviderUtils() {}

    public static boolean isPrivileged(AuthProvider authProvider) {
        return BooleanUtils.TRUE.equals(
                MetadataUtil.nullSafeLabels(authProvider).get(AuthProvider.PRIVILEGED_LABEL));
    }
}
