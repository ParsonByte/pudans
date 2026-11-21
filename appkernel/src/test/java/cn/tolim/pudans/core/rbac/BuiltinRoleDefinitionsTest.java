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
    void normalRoleDoesNotGrantConsoleMenus() {
        var closure = dependencyClosure(BuiltinEntities.NORMAL_ROLE);

        assertThat(closure).contains("authenticated");
        assertThat(closure).doesNotContainAnyElementsOf(workspaceMenuTemplates("console"));
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
