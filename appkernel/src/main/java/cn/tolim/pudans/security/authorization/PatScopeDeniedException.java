package cn.tolim.pudans.security.authorization;

import java.util.Collection;

public class PatScopeDeniedException extends RuntimeException {

    // 记录被拒绝的资源动作与已授予范围
    public PatScopeDeniedException(String resource, String verb, Collection<String> grantedScopes) {
        super("个人访问令牌的权限范围不允许 [" + resource + ':' + verb + "]，已授予的权限范围 "
                + grantedScopes);
    }
}
