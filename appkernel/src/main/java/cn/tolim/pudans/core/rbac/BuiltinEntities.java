package cn.tolim.pudans.core.rbac;

import cn.tolim.pudans.security.authorization.AuthorityUtils;

public final class BuiltinEntities {

    public static final String SUPER_ROLE = AuthorityUtils.SUPER_ROLE_NAME;

    public static final String SYSTEM_ADMIN_ROLE = "system-admin-role";

    public static final String NORMAL_ROLE = "normal-role";

    public static final String AI_OPERATOR_ROLE = "ai-operator-role";

    public static final String SUPER_ADMINS_GROUP = "super-admins";

    public static final String SYSTEM_ADMINS_GROUP = "system-admins";

    public static final String NORMAL_USERS_GROUP = "normal-users";

    public static final String BUILTIN_LABEL = "pudans.tolim.cn/builtin";

    // 内置团队与 Agent 使用的系统身份，不对应可登录用户。
    public static final String SYSTEM_USER = "system";

    private BuiltinEntities() {}

    public static String membershipBindingName(String username, String groupName) {
        return username + "-" + groupName + "-binding";
    }
}
