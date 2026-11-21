package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.security.contract.AuthProvider;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import org.apache.commons.lang3.BooleanUtils;

public final class AuthProviderUtils {

    private AuthProviderUtils() {}

    // 判断提供方是否带特权标记，特权提供方禁止停用。
    public static boolean isPrivileged(AuthProvider authProvider) {
        return BooleanUtils.TRUE.equals(
                MetadataUtil.nullSafeLabels(authProvider).get(AuthProvider.PRIVILEGED_LABEL));
    }
}
