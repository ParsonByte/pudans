package cn.tolim.pudans.contract.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.extension.api.Unstructured;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class AiProvidersButtonTemplatesTest {

    private static final String LOCATION =
            "classpath:/extensions/role/button/role-button-template-ai-providers.yaml";

    @Test
    void loadsProviderOperations() throws IOException {
        var resources = new PathMatchingResourcePatternResolver().getResources(LOCATION);
        var roles = new YamlUnstructuredLoader(resources)
                .load()
                .stream()
                .map(document -> Unstructured.OBJECT_MAPPER.convertValue(document.getData(), Role.class))
                .toList();

        assertThat(roles).hasSize(4);
        assertThat(roles)
                .allSatisfy(role -> {
                    assertThat(annotation(role, Role.UI_TYPE_ANNO)).isEqualTo(Role.UI_TYPE_BUTTON);
                    assertThat(annotation(role, Role.UI_MENU_ANNO)).isEqualTo("ai.models.providers");
                    assertThat(annotation(role, Role.UI_PERMISSIONS_ANNO)).contains("ai-providers:");
                    assertThat(annotation(role, Role.ROLE_DEPENDENCIES_ANNO))
                            .contains("role-template-view-ai-providers");
                    assertThat(role.getRules()).isNotEmpty();
                });
        assertThat(roles.stream()
                        .sorted(java.util.Comparator.comparingInt(
                                role -> Integer.parseInt(annotation(role, Role.UI_ORDER_ANNO))))
                        .map(role -> annotation(role, Role.UI_OPERATION_ANNO))
                        .toList())
                .containsExactly("create", "update", "delete", "test");
        assertThat(roles.stream()
                        .map(role -> annotation(role, Role.UI_LABEL_ANNO))
                        .toList())
                .doesNotHaveDuplicates();
        assertThat(roles.stream()
                        .map(role -> role.getMetadata().getName())
                        .toList())
                .doesNotHaveDuplicates();
    }

    private static String annotation(Role role, String key) {
        Map<String, String> annotations = role.getMetadata().getAnnotations();
        return annotations.get(key);
    }
}
