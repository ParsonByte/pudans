package cn.tolim.pudans.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.rbac.Role;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.yaml.snakeyaml.Yaml;

class MemoryRoleTemplateRulesTest {

    private static final String API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

    private static final String TEMPLATE = "role-api-template-agent.yaml";

    private static final String VIEW_ROLE = "role-template-view-personal-memories";

    private static final String MANAGE_ROLE = "role-template-manage-personal-memories";

    private static final String REVIEW_ROLE = "role-template-review-memory-pool";

    @Test
    void viewRoleAllowsPersonalReadsAndRetrieval() throws Exception {
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memories");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memories/-/personal");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memories/memory-a");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memories/memory-a/view");
        assertAllowed(VIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories/-/retrieve");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memoryjobs");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memoryjobs/-/mine");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memoryjobs/job-a");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memoryjobs/job-a/status");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memoryjobs/job-a/items");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memoryvectorspaces");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/understanding/-/map");
        assertAllowed(VIEW_ROLE, HttpMethod.GET, API_PREFIX + "/understanding/-/status");
        assertAllowed(VIEW_ROLE, HttpMethod.POST, API_PREFIX + "/understanding/-/query");
    }

    @Test
    void viewRoleDeniesPersonalWritesAndPoolReview() throws Exception {
        assertDenied(VIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories");
        assertDenied(VIEW_ROLE, HttpMethod.PATCH, API_PREFIX + "/memories/memory-a");
        assertDenied(VIEW_ROLE, HttpMethod.DELETE, API_PREFIX + "/memories/memory-a");
        assertDenied(VIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories/-/pool");
        assertDenied(VIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories/memory-a/-/approve");
    }

    @Test
    void manageRoleAllowsPersonalWritesAndJobs() throws Exception {
        assertAllowed(MANAGE_ROLE, HttpMethod.POST, API_PREFIX + "/memories");
        assertAllowed(MANAGE_ROLE, HttpMethod.PATCH, API_PREFIX + "/memories/memory-a");
        assertAllowed(MANAGE_ROLE, HttpMethod.DELETE, API_PREFIX + "/memories/memory-a");
        assertAllowed(MANAGE_ROLE, HttpMethod.POST, API_PREFIX + "/memories/memory-a/-/correct");
        assertAllowed(MANAGE_ROLE, HttpMethod.POST, API_PREFIX + "/memories/memory-a/-/withdraw");
        assertAllowed(MANAGE_ROLE, HttpMethod.POST, API_PREFIX + "/memoryjobs");
        assertAllowed(MANAGE_ROLE, HttpMethod.POST, API_PREFIX + "/memoryjobs/-/run");
    }

    @Test
    void reviewRoleAllowsPoolReviewAndVectorSync() throws Exception {
        assertAllowed(REVIEW_ROLE, HttpMethod.GET, API_PREFIX + "/memories/-/pool");
        assertAllowed(REVIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories/memory-a/-/approve");
        assertAllowed(REVIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories/memory-a/-/publish");
        assertAllowed(REVIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories/memory-a/-/reject");
        assertAllowed(REVIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memoryvectorspaces/-/sync");
        assertDenied(REVIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memories");
        assertDenied(REVIEW_ROLE, HttpMethod.POST, API_PREFIX + "/memoryjobs");
    }

    @Test
    @SuppressWarnings("unchecked")
    void manageRoleDependsOnViewRole() throws Exception {
        var metadata = (Map<String, Object>) role(MANAGE_ROLE).get("metadata");
        var annotations = (Map<String, Object>) metadata.get("annotations");
        assertThat((String) annotations.get("rbac.authorization.pudans.tolim.cn/dependencies"))
                .contains(VIEW_ROLE);
    }

    private static void assertAllowed(String roleName, HttpMethod method, String path)
            throws Exception {
        assertThat(evaluate(roleName, method, path))
                .as("expected allow for %s %s in %s", method, path, roleName)
                .isTrue();
    }

    private static void assertDenied(String roleName, HttpMethod method, String path)
            throws Exception {
        assertThat(evaluate(roleName, method, path))
                .as("expected deny for %s %s in %s", method, path, roleName)
                .isFalse();
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluate(String roleName, HttpMethod method, String path)
            throws Exception {
        var requestInfo =
                RequestInfoFactory.INSTANCE.newRequestInfo(
                        MockServerHttpRequest.method(method, path).build());
        var visitor = new AuthorizingVisitor(new AttributesRecord(requestInfo));
        var rules = (List<Map<String, Object>>) role(roleName).get("rules");
        if (rules == null) {
            return false;
        }
        for (var rule : rules) {
            visitor.visit(TEMPLATE, policyRule(rule), null);
        }
        return visitor.isAllowed();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> role(String roleName) throws Exception {
        try (InputStream input =
                new ClassPathResource("extensions/role/api/" + TEMPLATE).getInputStream()) {
            for (var document : new Yaml().loadAll(input)) {
                if (document instanceof Map<?, ?> role) {
                    var metadata = (Map<String, Object>) role.get("metadata");
                    if (metadata != null && roleName.equals(metadata.get("name"))) {
                        return (Map<String, Object>) role;
                    }
                }
            }
        }
        throw new AssertionError("role template not found: " + roleName);
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
