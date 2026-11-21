package cn.tolim.pudans.core.security.authorization;

import java.util.Collection;

public class PatScopeDeniedException extends RuntimeException {

    public PatScopeDeniedException(String resource, String verb, Collection<String> grantedScopes) {
        super("个人访问令牌的权限范围不允许 [" + resource + ':' + verb + "]，已授予的权限范围 "
                + grantedScopes);
    }
}
