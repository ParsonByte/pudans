package cn.tolim.pudans.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.rbac.Role;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.yaml.snakeyaml.Yaml;

class GenerationRoleTemplateRulesTest {

    private static final String API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

    private static final String TEMPLATE = "role-api-template-generation.yaml";

    @Test
    void viewRoleCoversOwnTaskRoutesOnly() throws Exception {
        var role = "role-template-view-generation-tasks";
        assertAllowed(role, HttpMethod.GET, API_PREFIX + "/generationtasks/-/mine");
        assertAllowed(role, HttpMethod.GET, API_PREFIX + "/generationtasks/task-a/status");
        assertAllowed(role, HttpMethod.GET, API_PREFIX + "/generationtasks/task-a/events");
        assertAllowed(role, HttpMethod.GET, API_PREFIX + "/generationtasks/task-a/result");
        assertDenied(role, HttpMethod.POST, API_PREFIX + "/generationtasks/-/start");
        assertDenied(role, HttpMethod.POST, API_PREFIX + "/generationtasks/task-a/cancel");
    }

    @Test
    void manageRoleCoversStartCancelAndRetry() throws Exception {
        var role = "role-template-manage-generation-tasks";
        assertAllowed(role, HttpMethod.POST, API_PREFIX + "/generationtasks/-/start");
        assertAllowed(role, HttpMethod.POST, API_PREFIX + "/generationtasks/task-a/cancel");
        assertAllowed(role, HttpMethod.POST, API_PREFIX + "/generationtasks/task-a/-/retry");
        assertDenied(role, HttpMethod.POST, API_PREFIX + "/generationtasks/task-a/events");
    }

    private static void assertAllowed(String roleName, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(roleName, method, path))
                .as("expected allow for %s %s in %s", method, path, roleName)
                .isTrue();
    }

    private static void assertDenied(String roleName, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(roleName, method, path))
                .as("expected deny for %s %s in %s", method, path, roleName)
                .isFalse();
    }

    private static boolean evaluate(String roleName, HttpMethod method, String path) throws Exception {
        var requestInfo =
                RequestInfoFactory.INSTANCE.newRequestInfo(
                        MockServerHttpRequest.method(method, path).build());
        var visitor = new AuthorizingVisitor(new AttributesRecord(requestInfo));
        for (var rule : rules(roleName)) {
            visitor.visit(TEMPLATE, rule, null);
        }
        return visitor.isAllowed();
    }

    @SuppressWarnings("unchecked")
    private static List<Role.PolicyRule> rules(String roleName) throws Exception {
        try (InputStream input =
                new ClassPathResource("extensions/role/api/" + TEMPLATE).getInputStream()) {
            var result = new ArrayList<Role.PolicyRule>();
            for (var document : new Yaml().loadAll(input)) {
                var role = (Map<String, Object>) document;
                if (role == null) {
                    continue;
                }
                var metadata = (Map<String, Object>) role.get("metadata");
                if (metadata == null || !roleName.equals(metadata.get("name"))) {
                    continue;
                }
                var rules = (List<Map<String, Object>>) role.get("rules");
                if (rules == null) {
                    continue;
                }
                rules.stream().map(GenerationRoleTemplateRulesTest::policyRule).forEach(result::add);
            }
            return result;
        }
    }

    private static Role.PolicyRule policyRule(Map<String, Object> rule) {
        return new Role.PolicyRule.Builder()
                .apiGroups(strings(rule.get("apiGroups")))
                .resources(strings(rule.get("resources")))
                .resourceNames(strings(rule.get("resourceNames")))
                .verbs(strings(rule.get("verbs")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static String[] strings(Object value) {
        return value == null ? new String[] {} : ((List<String>) value).toArray(String[]::new);
    }
}
