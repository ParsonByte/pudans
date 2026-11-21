package cn.tolim.pudans.contract.rbac.resource;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class AiWorkspaceMenuTemplatesTest {

    private static final String LOCATION = "classpath:/extensions/workspace/ai/*.yaml";

    @Test
    void loadsWorkspaceAndOrderedMenus() throws IOException {
        var resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        var roles = new YamlUnstructuredLoader(resources)
                .load()
                .stream()
                .map(document -> Unstructured.OBJECT_MAPPER.convertValue(document.getData(), Role.class))
                .toList();

        assertThat(roles).hasSize(26);

        var workspaces = roles.stream()
                .filter(role -> Role.UI_TYPE_WORKSPACE.equals(annotation(role, Role.UI_TYPE_ANNO)))
                .toList();
        assertThat(workspaces).hasSize(1);
        var workspace = workspaces.getFirst();
        assertThat(workspace.getMetadata().getName()).isEqualTo("role-template-workspace-ai");
        assertThat(annotation(workspace, Role.UI_WORKSPACE_ANNO)).isEqualTo("ai");
        assertThat(annotation(workspace, Role.UI_LABEL_ANNO)).isEqualTo("PUDANS AI Console");
        assertThat(annotation(workspace, Role.UI_ORDER_ANNO)).isEqualTo("30");

        var menus = roles.stream()
                .filter(role -> Role.UI_TYPE_MENU.equals(annotation(role, Role.UI_TYPE_ANNO)))
                .sorted(Comparator.comparingInt(menu -> order(menu)))
                .toList();
        assertThat(menus).hasSize(25);
        assertThat(menus)
                .allSatisfy(menu -> {
                    assertThat(annotation(menu, Role.UI_WORKSPACE_ANNO)).isEqualTo("ai");
                    assertThat(annotation(menu, Role.UI_PATH_ANNO)).startsWith("/ai/");
                    assertThat(annotation(menu, Role.UI_COMPONENT_ANNO)).isNotBlank();
                    assertThat(annotation(menu, Role.UI_LABEL_ANNO)).isNotBlank();
                    assertThat(annotation(menu, Role.UI_PERMISSIONS_ANNO)).contains(":view");
                    assertThat(annotation(menu, Role.UI_GROUP_ANNO)).isIn("使用", "模型", "观测", "配置");
                });
        assertThat(menus)
                .extracting(menu -> menu.getMetadata().getName())
                .doesNotHaveDuplicates();
        assertThat(menus)
                .extracting(menu -> annotation(menu, Role.UI_PATH_ANNO))
                .doesNotHaveDuplicates();
        assertThat(menus)
                .extracting(menu -> annotation(menu, Role.UI_ORDER_ANNO))
                .doesNotHaveDuplicates();
        assertThat(menus)
                .extracting(menu -> annotation(menu, Role.UI_GROUP_ANNO))
                .containsSubsequence("使用", "模型", "观测", "配置");

        assertThat(parentLabels(menus, "资源库"))
                .containsExactly(
                        "指令", "记忆", "MCP 服务器", "技能", "Hooks", "环境变量", "子代理", "斜杠命令");
        assertThat(parentLabels(menus, "用量")).containsExactly("概览", "请求", "会话", "上下文");
        assertThat(menus.stream()
                        .filter(menu -> "模型".equals(annotation(menu, Role.UI_GROUP_ANNO)))
                        .map(menu -> annotation(menu, Role.UI_LABEL_ANNO))
                        .toList())
                .containsExactly("供应商", "模型广场", "路由", "聊天模式", "路由组", "网关");
    }

    private static List<String> parentLabels(List<Role> menus, String parent) {
        return menus.stream()
                .filter(menu -> parent.equals(annotation(menu, Role.UI_PARENT_ANNO)))
                .map(menu -> annotation(menu, Role.UI_LABEL_ANNO))
                .toList();
    }

    private static int order(Role menu) {
        return Integer.parseInt(annotation(menu, Role.UI_ORDER_ANNO));
    }

    private static String annotation(Role role, String key) {
        return role.getMetadata().getAnnotations().get(key);
    }
}
