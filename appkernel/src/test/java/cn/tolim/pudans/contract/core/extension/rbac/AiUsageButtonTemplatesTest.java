package cn.tolim.pudans.contract.core.extension.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class AiUsageButtonTemplatesTest {

    private static final String BASE = "classpath:/extensions/role/button/role-button-template-ai-usage-";

    @Test
    void loadsTheUsageOperationsOfEveryPage() throws IOException {
        var all = new ArrayList<Role>();
        assertTemplate(all, "ai.usage.overview", "overview", "ai-usage-overview:");
        assertTemplate(all, "ai.usage.requests", "requests", "ai-usage-requests:");
        assertTemplate(all, "ai.usage.sessions", "sessions", "ai-usage-sessions:");
        assertTemplate(all, "ai.usage.context", "context", "ai-usage-context:");

        assertThat(all).hasSize(8);
        assertThat(all.stream().map(role -> role.getMetadata().getName()).toList())
                .doesNotHaveDuplicates();
    }

    private static void assertTemplate(
            List<Role> all, String menu, String page, String permissionPrefix) throws IOException {
        var roles = load(BASE + page + ".yaml");
        all.addAll(roles);

        assertThat(roles).hasSize(2);
        assertThat(roles)
                .allSatisfy(role -> {
                    assertThat(annotation(role, Role.UI_TYPE_ANNO)).isEqualTo(Role.UI_TYPE_BUTTON);
                    assertThat(annotation(role, Role.UI_MENU_ANNO)).isEqualTo(menu);
                    assertThat(annotation(role, Role.UI_PERMISSIONS_ANNO)).contains(permissionPrefix);
                    assertThat(annotation(role, Role.ROLE_DEPENDENCIES_ANNO))
                            .contains("role-template-view-ai-usage-" + page);
                    assertThat(role.getRules()).isNotEmpty();
                    assertThat(role.getRules())
                            .allSatisfy(rule -> {
                                assertThat(rule.getApiGroups()).containsExactly("api.pudans.tolim.cn");
                                assertThat(rule.getResources()).containsExactly("aistats/usage");
                                assertThat(rule.getVerbs()).contains("get", "list");
                            });
                });
        assertThat(roles.stream()
                        .sorted(Comparator.comparingInt(
                                role -> Integer.parseInt(annotation(role, Role.UI_ORDER_ANNO))))
                        .map(role -> annotation(role, Role.UI_OPERATION_ANNO))
                        .toList())
                .containsExactly("view", "export");
    }

    private static List<Role> load(String location) throws IOException {
        var resources = new PathMatchingResourcePatternResolver().getResources(location);
        return new YamlUnstructuredLoader(resources)
                .load()
                .stream()
                .map(document -> Unstructured.OBJECT_MAPPER.convertValue(document.getData(), Role.class))
                .toList();
    }

    private static String annotation(Role role, String key) {
        Map<String, String> annotations = role.getMetadata().getAnnotations();
        return annotations.get(key);
    }
}