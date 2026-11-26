package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.extension.contract.MetadataUtil;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class BuiltinRoleDefinitionsTest {

    private final BuiltinRoleDefinitions definitions = new BuiltinRoleDefinitions();

    @Test
    void loadsBuiltinRolesAndGroupsFromRoleResources() {
        assertThat(definitions.builtinRoles())
                .extracting(role -> role.getMetadata().getName())
                .containsExactlyInAnyOrder(
                        BuiltinEntities.SUPER_ROLE,
                        BuiltinEntities.SYSTEM_ADMIN_ROLE,
                        BuiltinEntities.NORMAL_ROLE);
        assertThat(definitions.builtinGroups())
                .extracting(group -> group.getMetadata().getName())
                .containsExactlyInAnyOrder(
                        BuiltinEntities.SUPER_ADMINS_GROUP,
                        BuiltinEntities.SYSTEM_ADMINS_GROUP,
                        BuiltinEntities.NORMAL_USERS_GROUP);
    }

    @Test
    void roleLookupOnlyMatchesBuiltinDefinitions() {
        assertThat(definitions.role(BuiltinEntities.SYSTEM_ADMIN_ROLE)).isPresent();
        assertThat(definitions.role("role-template-view-users")).isEmpty();
        assertThat(definitions.group(BuiltinEntities.SUPER_ADMINS_GROUP)).isPresent();
        assertThat(definitions.group("missing-group")).isEmpty();
    }

    @Test
    void allRolesIncludeBuiltinRolesAndRoleTemplates() {
        var builtinNames = definitions.builtinRoles().stream()
                .map(role -> role.getMetadata().getName())
                .toList();

        assertThat(definitions.allRoles())
                .extracting(role -> role.getMetadata().getName())
                .containsAll(builtinNames)
                .contains("role-template-view-users");
        assertThat(definitions.allRoles()).hasSizeGreaterThan(builtinNames.size());
    }

    @Test
    void systemAdminRoleCoversEveryConsoleOperationTemplate() {
        assertThat(dependencyClosure(BuiltinEntities.SYSTEM_ADMIN_ROLE))
                .containsAll(operationTemplates(menu -> menu.startsWith("console.")));
    }

    @Test
    void systemAdminRoleKeepsPersonalAccountPermissions() {
        assertThat(dependencyClosure(BuiltinEntities.SYSTEM_ADMIN_ROLE))
                .contains(BuiltinEntities.NORMAL_ROLE, "role-template-own-user-info");
    }

    @Test
    void systemAdminGetsEveryDansConsoleMenu() {
        var dansMenus = definitions.allRoles().stream()
                .filter(role -> "dans".equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_GROUP_KEY_ANNO)))
                .toList();

        assertThat(dansMenus)
                .extracting(role -> MetadataUtil.nullSafeAnnotations(role).get(Role.UI_COMPONENT_ANNO))
                .containsExactlyInAnyOrder(
                        "dans-overview",
                        "ai-channels",
                        "ai-models",
                        "ai-billing",
                        "ai-catalog",
                        "model-plaza",
                        "agent-sessions",
                        "agent-sandboxes",
                        "agent-memory",
                        "creation-tasks",
                        "creation-assets",
                        "creation-content");
        assertThat(dansMenus).allSatisfy(role -> {
            var annotations = MetadataUtil.nullSafeAnnotations(role);
            assertThat(annotations.get(Role.UI_WORKSPACE_ANNO)).isEqualTo("console");
            assertThat(annotations.get(Role.UI_GROUP_ANNO)).isEqualTo("DANS 管控");
            assertThat(annotations.get(Role.UI_PARENT_ANNO)).isNotBlank();
        });
        assertThat(dependencyClosure(BuiltinEntities.SYSTEM_ADMIN_ROLE))
                .containsAll(dansMenus.stream()
                        .map(role -> role.getMetadata().getName())
                        .toList());
    }

    @Test
    void systemAdminGetsEveryDansAdminCapability() {
        assertThat(dependencyClosure(BuiltinEntities.SYSTEM_ADMIN_ROLE))
                .contains(
                        "role-template-view-all-ai-channels",
                        "role-template-view-all-ai-catalog",
                        "role-template-view-all-ai-default-models",
                        "role-template-view-all-agent-sessions",
                        "role-template-view-all-agent-sandboxes",
                        "role-template-view-all-memory-jobs",
                        "role-template-view-all-generation-tasks",
                        "role-template-force-cancel-generation-tasks",
                        "role-template-view-all-creation-assets",
                        "role-template-view-all-creation-content",
                        "role-template-approve-creation-content",
                        "role-template-reject-creation-content",
                        "role-template-publish-creation-content",
                        "role-template-unpublish-creation-content");
    }

    @Test
    void normalRoleDoesNotGrantConsoleMenus() {
        var closure = dependencyClosure(BuiltinEntities.NORMAL_ROLE);

        assertThat(closure).contains("authenticated");
        assertThat(closure).doesNotContainAnyElementsOf(workspaceMenuTemplates("console"));
    }

    @Test
    void workspaceTemplatesDeclareDynamicNavigationMetadata() {
        var workspaces = definitions.allRoles().stream()
                .filter(role -> Role.UI_TYPE_WORKSPACE.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO)))
                .toList();

        assertThat(workspaces).isNotEmpty().allSatisfy(role -> {
            var annotations = MetadataUtil.nullSafeAnnotations(role);
            assertThat(annotations.get(Role.UI_SLUG_ANNO)).isNotBlank();
        });
    }

    @Test
    void personalCenterMenusBelongToTheUcWorkspace() {
        var allMenus = definitions.allRoles().stream()
                .filter(role -> Role.UI_TYPE_MENU.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO)))
                .toList();
        var workspaces = definitions.allRoles().stream()
                .filter(role -> Role.UI_TYPE_WORKSPACE.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO)))
                .toList();
        var personalCenter = workspaces.stream()
                .filter(role -> "uc".equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO)))
                .toList();
        var managementConsole = workspaces.stream()
                .filter(role -> "console".equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO)))
                .toList();
        var menus = allMenus.stream()
                .filter(role -> "uc".equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO)))
                .toList();

        assertThat(workspaces)
                .extracting(role -> MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO))
                .contains("console", "index", "uc")
                .doesNotContain("default");
        assertThat(personalCenter).singleElement().satisfies(role -> {
            var annotations = MetadataUtil.nullSafeAnnotations(role);
            assertThat(annotations.get(Role.UI_SLUG_ANNO)).isEqualTo("uc");
            assertThat(annotations.get(Role.UI_LABEL_ANNO)).isEqualTo("个人中心");
            assertThat(annotations.get(Role.UI_DESCRIPTION_ANNO)).isEqualTo("个人中心");
        });
        assertThat(managementConsole).singleElement().satisfies(role -> {
            var annotations = MetadataUtil.nullSafeAnnotations(role);
            assertThat(annotations.get(Role.UI_SLUG_ANNO)).isEqualTo("console");
            assertThat(annotations.get(Role.UI_LABEL_ANNO)).isEqualTo("管理控制台");
            assertThat(annotations.get(Role.UI_DESCRIPTION_ANNO)).isEqualTo("管理控制台");
        });
        assertThat(menus)
                .extracting(role -> role.getMetadata().getName())
                .containsExactlyInAnyOrder(
                        "role-template-view-account-profile",
                        "role-template-view-account-security",
                        "role-template-view-account-identities");
        assertThat(menus)
                .extracting(role -> MetadataUtil.nullSafeAnnotations(role).get(Role.UI_COMPONENT_ANNO))
                .containsExactlyInAnyOrder("profile", "security", "identities");
        assertThat(menus)
                .extracting(role -> MetadataUtil.nullSafeAnnotations(role).get(Role.UI_MENU_ANNO))
                .containsExactlyInAnyOrder(
                        "uc.account.profile", "uc.account.security", "uc.account.identities");
        assertThat(allMenus)
                .extracting(role -> MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO))
                .doesNotContain("default");
    }

    @Test
    void indexWorkspaceProvidesThePublicHomePage() {
        var workspaces = definitions.allRoles().stream()
                .filter(role -> Role.UI_TYPE_WORKSPACE.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO)))
                .filter(role -> "index".equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO)))
                .toList();
        var menus = definitions.allRoles().stream()
                .filter(role -> Role.UI_TYPE_MENU.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO)))
                .filter(role -> "index".equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO)))
                .toList();

        assertThat(workspaces).singleElement().satisfies(role -> {
            var annotations = MetadataUtil.nullSafeAnnotations(role);
            assertThat(annotations.get(Role.UI_SLUG_ANNO)).isEqualTo("index");
            assertThat(annotations.get(Role.UI_LABEL_ANNO)).isEqualTo("首页");
            assertThat(annotations.get(Role.UI_ORDER_ANNO)).isEqualTo("1");
            assertThat(annotations.get(Role.UI_PUBLIC_ANNO)).isEqualTo("true");
        });
        assertThat(menus).isEmpty();
    }

    @Test
    void menuViewTemplatesDeclareDynamicGroupMetadata() {
        var menus = definitions.allRoles().stream()
                .filter(role -> Role.UI_TYPE_MENU.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO)))
                .filter(role -> Role.UI_ACTION_VIEW.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_ACTION_ANNO)))
                .toList();

        assertThat(menus).isNotEmpty().allSatisfy(role -> {
            var annotations = MetadataUtil.nullSafeAnnotations(role);
            assertThat(annotations.get(Role.UI_GROUP_KEY_ANNO)).isNotBlank();
        });
    }

    private Set<String> workspaceMenuTemplates(String workspace) {
        return definitions.allRoles().stream()
                .filter(role -> role.getMetadata() != null)
                .filter(role -> Role.UI_TYPE_MENU.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO)))
                .filter(role -> workspace.equals(
                        MetadataUtil.nullSafeAnnotations(role).get(Role.UI_WORKSPACE_ANNO)))
                .map(role -> role.getMetadata().getName())
                .collect(Collectors.toSet());
    }

    private Set<String> operationTemplates(Predicate<String> menuFilter) {
        return definitions.allRoles().stream()
                .filter(role -> role.getMetadata() != null)
                .filter(role -> {
                    var type = MetadataUtil.nullSafeAnnotations(role).get(Role.UI_TYPE_ANNO);
                    return Role.UI_TYPE_BUTTON.equals(type) || Role.UI_TYPE_TABLE.equals(type);
                })
                .filter(role -> menuFilter.test(
                        MetadataUtil.nullSafeAnnotations(role).getOrDefault(Role.UI_MENU_ANNO, "")))
                .map(role -> role.getMetadata().getName())
                .collect(Collectors.toSet());
    }

    private Set<String> dependencyClosure(String root) {
        var roles = definitions.allRoles().stream()
                .filter(role -> role.getMetadata() != null)
                .collect(Collectors.toMap(
                        role -> role.getMetadata().getName(), role -> role, (first, second) -> first));
        var visited = new HashSet<String>();
        var pending = new ArrayDeque<String>();
        pending.add(root);
        while (!pending.isEmpty()) {
            var name = pending.removeFirst();
            if (!visited.add(name)) {
                continue;
            }
            var role = roles.get(name);
            if (role == null) {
                continue;
            }
            var raw = MetadataUtil.nullSafeAnnotations(role).get(Role.ROLE_DEPENDENCIES_ANNO);
            if (raw == null || raw.isBlank()) {
                continue;
            }
            var dependencies = JsonUtils.jsonToObject(raw, new TypeReference<List<String>>() {});
            if (dependencies != null) {
                dependencies.stream()
                        .filter(Objects::nonNull)
                        .forEach(pending::addLast);
            }
        }
        return visited;
    }
}
