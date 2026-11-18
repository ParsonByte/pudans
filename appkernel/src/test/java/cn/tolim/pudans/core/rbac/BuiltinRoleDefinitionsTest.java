package cn.tolim.pudans.core.rbac;

import static org.assertj.core.api.Assertions.assertThat;

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
}
