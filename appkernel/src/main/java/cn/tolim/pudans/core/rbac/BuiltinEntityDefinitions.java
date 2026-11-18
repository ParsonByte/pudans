package cn.tolim.pudans.core.rbac;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.Role.PolicyRule;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.MetadataUtil;

public final class BuiltinEntityDefinitions {

    public static final String DISPLAY_NAME_ANNO = "rbac.authorization.pudans.tolim.cn/display-name";

    private BuiltinEntityDefinitions() {}

    public static Optional<Role> role(String name) {
        if (BuiltinEntities.SUPER_ROLE.equals(name)) {
            return Optional.of(superRole());
        }
        if (BuiltinEntities.SYSTEM_ADMIN_ROLE.equals(name)) {
            return Optional.of(systemAdminRole());
        }
        if (BuiltinEntities.NORMAL_ROLE.equals(name)) {
            return Optional.of(normalRole());
        }
        return Optional.empty();
    }

    public static Optional<UserGroup> group(String name) {
        if (BuiltinEntities.SUPER_ADMINS_GROUP.equals(name)) {
            return Optional.of(superAdminsGroup());
        }
        if (BuiltinEntities.SYSTEM_ADMINS_GROUP.equals(name)) {
            return Optional.of(systemAdminsGroup());
        }
        if (BuiltinEntities.NORMAL_USERS_GROUP.equals(name)) {
            return Optional.of(normalUsersGroup());
        }
        return Optional.empty();
    }

    public static String membershipBindingName(String username, String groupName) {
        return username + "-" + groupName + "-binding";
    }

    public static Role superRole() {
        var role = role(BuiltinEntities.SUPER_ROLE, "超级管理员");
        role.setRules(List.of(rule().apiGroups("*")
                .resources("*")
                .nonResourceURLs("*")
                .verbs("*")
                .build()));
        MetadataUtil.nullSafeAnnotations(role).put(Role.UI_PERMISSIONS_ANNO, "[\"*\"]");
        return role;
    }

    public static Role systemAdminRole() {
        var role = role(BuiltinEntities.SYSTEM_ADMIN_ROLE, "系统管理员");
        MetadataUtil.nullSafeAnnotations(role).put(
                Role.UI_PERMISSIONS_ANNO,
                "[\"dashboard:view\", \"users:manage\", \"roles:manage\", \"audit:view\", "
                        + "\"risk:manage\", \"webhooks:manage\", \"settings:manage\", "
                        + "\"auth-providers:manage\", \"ops:view\"]");
        role.setRules(List.of(
                rule().apiGroups(ApiGroups.API)
                        .resources(
                                "users",
                                "users/batch",
                                "users/local",
                                "users/password",
                                "users/permissions",
                                "users/usergroup",
                                "users/connections",
                                "users/activity",
                                "users/ui-permissions",
                                "users/self")
                        .verbs("get", "list", "create", "update", "patch", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("dashboard")
                        .verbs("get")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("usergroups", "usergroupbindings")
                        .verbs("get", "list", "create", "update", "patch", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources(
                                "roles",
                                "rolebindings",
                                "temprolebindings",
                                "temprolebindings/approve",
                                "temprolebindings/revoke")
                        .verbs("get", "list", "create", "update", "patch", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("auditlogs", "auditlogs/graph", "auditlogs/trace", "auditlogs/replay")
                        .verbs("get", "list")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("riskrules", "webhooks")
                        .verbs("get", "list", "create", "update", "patch", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("authproviders")
                        .verbs("get", "list", "create", "update", "patch", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("auth-providers", "auth-providers/enable", "auth-providers/disable")
                        .verbs("get", "list", "update")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("settings")
                        .verbs("get", "list", "create", "update", "patch", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("configmaps", "secrets", "remembermetokens")
                        .verbs("get", "list", "create", "update", "patch", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("users/disable", "users/enable")
                        .verbs("create")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("parameter-settings", "parameter-settings/-")
                        .verbs("get", "list", "create", "update", "patch")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("systemconfigs")
                        .verbs("get", "list", "update", "patch")
                        .build(),
                rule()
                        .nonResourceURLs("/actuator", "/actuator/*")
                        .verbs("get")
                        .build()));
        return role;
    }

    public static Role normalRole() {
        var role = role(BuiltinEntities.NORMAL_ROLE, "普通用户");
        MetadataUtil.nullSafeAnnotations(role).put(
                Role.UI_PERMISSIONS_ANNO,
                "[\"profile:manage\", \"authentications:manage\"]");
        role.setRules(List.of(
                rule().apiGroups(ApiGroups.API)
                        .resources("users", "users/permissions", "users/ui-permissions")
                        .resourceNames("-")
                        .verbs("get", "list")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("users")
                        .resourceNames("-")
                        .verbs("update")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("users/password")
                        .resourceNames("-")
                        .verbs("update")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("users/self")
                        .resourceNames("-")
                        .verbs("get")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("users/self")
                        .resourceNames("-")
                        .verbs("update")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("users/avatar", "users/announcement-state")
                        .resourceNames("-")
                        .verbs("get", "create", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("avatars")
                        .verbs("get")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("authentications", "authentications/totp", "authentications/settings")
                        .verbs("*")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("personalaccesstokens")
                        .verbs("*")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("devices")
                        .verbs("get", "list", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("user-preferences", "annotationsettings")
                        .verbs("get", "list", "create", "update", "patch")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources(
                                "userconnections",
                                "userconnections/oidc/providers",
                                "userconnections/oidc/bind")
                        .verbs("create", "get", "list", "delete")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("passkeys", "passkeys/registration/options", "passkeys/registration/verify")
                        .verbs("create", "get", "list")
                        .build(),
                rule().apiGroups(ApiGroups.API)
                        .resources("authproviders")
                        .verbs("get", "list")
                        .build()));
        return role;
    }

    public static UserGroup superAdminsGroup() {
        return group(
                BuiltinEntities.SUPER_ADMINS_GROUP,
                "超级管理员组",
                "内置超级管理员组，绑定 super-role。",
                BuiltinEntities.SUPER_ROLE);
    }

    public static UserGroup systemAdminsGroup() {
        return group(
                BuiltinEntities.SYSTEM_ADMINS_GROUP,
                "系统管理员组",
                "内置系统管理员组，绑定 system-admin-role。",
                BuiltinEntities.SYSTEM_ADMIN_ROLE);
    }

    public static UserGroup normalUsersGroup() {
        return group(
                BuiltinEntities.NORMAL_USERS_GROUP,
                "普通用户组",
                "内置普通用户组，绑定 normal-role，飞书同步与批量创建的用户默认加入该组。",
                BuiltinEntities.NORMAL_ROLE);
    }

    private static Role role(String name, String displayName) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setFinalizers(Set.of(MetadataUtil.SYSTEM_FINALIZER));
        var labels = new HashMap<String, String>();
        labels.put(BuiltinEntities.BUILTIN_LABEL, "true");
        labels.put(Role.SYSTEM_RESERVED_LABELS, "true");
        metadata.setLabels(labels);

        var role = new Role();
        role.setMetadata(metadata);
        MetadataUtil.nullSafeAnnotations(role).put(DISPLAY_NAME_ANNO, displayName);
        return role;
    }

    private static UserGroup group(String name, String displayName, String description, String roleName) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setFinalizers(Set.of(MetadataUtil.SYSTEM_FINALIZER));
        var labels = new HashMap<String, String>();
        labels.put(BuiltinEntities.BUILTIN_LABEL, "true");
        metadata.setLabels(labels);

        var roleRef = new UserGroup.RoleRef();
        roleRef.setKind(Role.KIND);
        roleRef.setName(roleName);
        roleRef.setApiGroup(Role.GROUP);

        var group = new UserGroup();
        group.setMetadata(metadata);
        group.setDisplayName(displayName);
        group.setDescription(description);
        group.setRoleRefs(new ArrayList<>(List.of(roleRef)));
        return group;
    }

    private static PolicyRule.Builder rule() {
        return new PolicyRule.Builder();
    }
}