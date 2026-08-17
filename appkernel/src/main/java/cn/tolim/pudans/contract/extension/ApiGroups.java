package cn.tolim.pudans.contract.extension;

import java.util.Set;

/** The only API groups allowed by the pudans HTTP contract. */
public final class ApiGroups {

    public static final String CONSOLE = "console.api.pudans.tolim.cn";

    public static final String UC = "uc.api.pudans.tolim.cn";

    public static final String PUBLIC = "public.api.pudans.tolim.cn";

    public static final String EXTENSION = "extension.api.pudans.tolim.cn";

    public static final String AUTH = "auth.api.pudans.tolim.cn";

    public static final Set<String> ALL = Set.of(CONSOLE, UC, PUBLIC, EXTENSION, AUTH);

    public static final String PUBLIC_SETUP_PATH =
            "/apis/" + PUBLIC + "/v1alpha1/system/setup";

    private ApiGroups() {}

    /**
     * Maps every extension kind to one of the four public API domains.
     * Console: administrative resources; UC: current-user resources;
     * Public: unauthenticated resources; Extension: plugin/core extension
     * machinery.
     */
    public static String extensionGroup(String kind) {
        return switch (kind) {
            case "AnnotationSetting", "Setting" -> UC;
            case "RememberMeToken", "UserConnection", "PersonalAccessToken", "Device", "AuthProvider" -> AUTH;
            case "Plugin", "ReverseProxy", "ExtensionDefinition", "ExtensionPointDefinition" -> EXTENSION;
            case "User", "Secret", "ConfigMap", "RoleBinding", "UserGroupBinding",
                    "UserGroup", "TempRoleBinding", "Role", "AuditRecord", "Webhook", "RiskRule" -> CONSOLE;
            default -> throw new IllegalStateException(
                    "Extension kind " + kind + " is not assigned to a canonical API group");
        };
    }
}
