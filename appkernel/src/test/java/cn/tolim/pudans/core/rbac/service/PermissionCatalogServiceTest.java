package cn.tolim.pudans.core.rbac.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;

@ExtendWith(MockitoExtension.class)
class PermissionCatalogServiceTest {

    @Mock
    ReactiveExtensionClient client;

    private PermissionCatalogService service;

    @BeforeEach
    void setUp() {
        service = new PermissionCatalogService(client);
    }

    private static Map<String, String> annotations(String... pairs) {
        var annotations = new HashMap<String, String>();
        for (var index = 0; index < pairs.length; index += 2) {
            annotations.put(pairs[index], pairs[index + 1]);
        }
        return annotations;
    }

    private static Role template(String name, Map<String, String> annotations) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setLabels(Map.of(Role.TEMPLATE_LABEL_NAME, "true"));
        metadata.setAnnotations(annotations);
        var role = new Role();
        role.setMetadata(metadata);
        role.setRules(List.of());
        return role;
    }

    private void stubTemplates(Role... roles) {
        when(client.listAll(eq(Role.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(roles));
    }

    private static List<Role> catalogRoles() {
        return List.of(
                template(
                        "role-template-workspace-default",
                        annotations(
                                Role.UI_TYPE_ANNO, Role.UI_TYPE_WORKSPACE,
                                Role.UI_WORKSPACE_ANNO, "default",
                                Role.UI_LABEL_ANNO, "默认",
                                Role.UI_DESCRIPTION_ANNO, "工作区",
                                Role.UI_ORDER_ANNO, "10")),
                template(
                        "role-template-workspace-console",
                        annotations(
                                Role.UI_TYPE_ANNO, Role.UI_TYPE_WORKSPACE,
                                Role.UI_WORKSPACE_ANNO, "console",
                                Role.UI_LABEL_ANNO, "终端管控",
                                Role.UI_DESCRIPTION_ANNO, "统一终端访问管控",
                                Role.UI_ORDER_ANNO, "20")),
                template(
                        "role-template-view-overview",
                        annotations(
                                Role.UI_TYPE_ANNO, Role.UI_TYPE_MENU,
                                Role.UI_ACTION_ANNO, Role.UI_ACTION_VIEW,
                                Role.UI_MENU_ANNO, "default.overview",
                                Role.UI_WORKSPACE_ANNO, "default",
                                Role.UI_PATH_ANNO, "/",
                                Role.UI_LABEL_ANNO, "总览",
                                Role.UI_GROUP_ANNO, "默认工作区",
                                Role.UI_ORDER_ANNO, "10",
                                Role.UI_PUBLIC_ANNO, "true")),
                template(
                        "role-template-view-users",
                        annotations(
                                Role.UI_TYPE_ANNO, Role.UI_TYPE_MENU,
                                Role.UI_ACTION_ANNO, Role.UI_ACTION_VIEW,
                                Role.UI_MENU_ANNO, "console.users",
                                Role.UI_WORKSPACE_ANNO, "console",
                                Role.UI_PATH_ANNO, "/console/users",
                                Role.UI_LABEL_ANNO, "用户",
                                Role.UI_COMPONENT_ANNO, "users",
                                Role.UI_GROUP_ANNO, "控制",
                                Role.UI_ORDER_ANNO, "20",
                                Role.UI_PERMISSIONS_ANNO, "[\"users:view\"]")),
                template(
                        "role-template-manage-users",
                        annotations(
                                Role.UI_TYPE_ANNO, Role.UI_TYPE_MENU,
                                Role.UI_ACTION_ANNO, Role.UI_ACTION_MANAGE,
                                Role.UI_MENU_ANNO, "console.users",
                                Role.UI_PERMISSIONS_ANNO, "[\"users:manage\"]")),
                template(
                        "role-template-create-users",
                        annotations(
                                Role.UI_TYPE_ANNO, Role.UI_TYPE_BUTTON,
                                Role.UI_OPERATION_ANNO, "create",
                                Role.UI_MENU_ANNO, "console.users",
                                Role.UI_LABEL_ANNO, "创建用户",
                                Role.UI_ORDER_ANNO, "10",
                                Role.UI_PERMISSIONS_ANNO, "[\"users:create\"]")));
    }

    @Test
    void catalogGroupsTemplatesIntoWorkspacesMenusAndOperations() {
        stubTemplates(catalogRoles().toArray(Role[]::new));

        StepVerifier.create(service.catalog())
                .assertNext(catalog -> {
                    assertThat(catalog.workspaces()).hasSize(2);
                    var defaultWorkspace = catalog.workspaces().get(0);
                    assertThat(defaultWorkspace.id()).isEqualTo("default");
                    assertThat(defaultWorkspace.menus()).hasSize(1);
                    assertThat(defaultWorkspace.menus().get(0).publicMenu()).isTrue();

                    var consoleWorkspace = catalog.workspaces().get(1);
                    assertThat(consoleWorkspace.id()).isEqualTo("console");
                    var users = consoleWorkspace.menus().get(0);
                    assertThat(users.key()).isEqualTo("console.users");
                    assertThat(users.path()).isEqualTo("/console/users");
                    assertThat(users.component()).isEqualTo("users");
                    assertThat(users.view().permission()).isEqualTo("users:view");
                    assertThat(users.manage().permission()).isEqualTo("users:manage");
                    assertThat(users.operations()).hasSize(1);
                    assertThat(users.operations().get(0).key()).isEqualTo("create");
                    assertThat(users.operations().get(0).permission()).isEqualTo("users:create");
                })
                .verifyComplete();
    }

    @Test
    void managePermissionExpandsToViewAndOperationPermissions() {
        stubTemplates(catalogRoles().toArray(Role[]::new));

        StepVerifier.create(service.expand(List.of("users:manage")))
                .assertNext(permissions -> assertThat(permissions)
                        .containsExactly("users:manage", "users:view", "users:create"))
                .verifyComplete();
    }

    @Test
    void wildcardExpandsToWildcard() {
        StepVerifier.create(service.expand(List.of("*")))
                .assertNext(permissions -> assertThat(permissions).containsExactly("*"))
                .verifyComplete();
    }

    @Test
    void navigationListsOnlyAccessibleMenus() {
        stubTemplates(catalogRoles().toArray(Role[]::new));

        StepVerifier.create(service.navigation(List.of("users:view")))
                .assertNext(navigation -> {
                    assertThat(navigation.workspaces()).hasSize(2);
                    var defaultWorkspace = navigation.workspaces().get(0);
                    assertThat(defaultWorkspace.menus()).extracting(PermissionCatalogService.NavigationMenu::key)
                            .containsExactly("default.overview");
                    var consoleWorkspace = navigation.workspaces().get(1);
                    assertThat(consoleWorkspace.homePath()).isEqualTo("/console/users");
                    assertThat(consoleWorkspace.menus()).extracting(PermissionCatalogService.NavigationMenu::key)
                            .containsExactly("console.users");
                    assertThat(consoleWorkspace.menus().getFirst().component()).isEqualTo("users");
                    assertThat(consoleWorkspace.menus().getFirst().operations())
                            .extracting(PermissionCatalogService.NavigationOperation::key)
                            .containsExactly("create");
                    assertThat(consoleWorkspace.menus().getFirst().operations().getFirst().allowed())
                            .isFalse();
                })
                .verifyComplete();
    }

    @Test
    void navigationMarksGrantedOperationsAsAllowed() {
        stubTemplates(catalogRoles().toArray(Role[]::new));

        StepVerifier.create(service.navigation(List.of("users:view", "users:create")))
                .assertNext(navigation -> {
                    var consoleWorkspace = navigation.workspaces().get(1);
                    assertThat(consoleWorkspace.menus().getFirst().operations().getFirst().allowed())
                            .isTrue();
                })
                .verifyComplete();
    }

    @Test
    void navigationAlwaysIncludesPublicMenus() {
        stubTemplates(catalogRoles().toArray(Role[]::new));

        StepVerifier.create(service.navigation(List.of()))
                .assertNext(navigation -> {
                    assertThat(navigation.workspaces()).hasSize(1);
                    assertThat(navigation.workspaces().get(0).menus())
                            .extracting(PermissionCatalogService.NavigationMenu::key)
                            .containsExactly("default.overview");
                })
                .verifyComplete();
    }
}
