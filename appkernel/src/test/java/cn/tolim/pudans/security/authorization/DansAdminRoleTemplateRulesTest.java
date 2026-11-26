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

class DansAdminRoleTemplateRulesTest {

    private static final String API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

    private static final String TEMPLATE = "role-api-template-dans.yaml";

    private static final String AGENT_TEMPLATE = "role-api-template-agent.yaml";

    private static final String GENERATION_TEMPLATE = "role-api-template-generation.yaml";

    private static final String BUTTON_TEMPLATE = "role-button-template-dans.yaml";

    @Test
    void aiRolesAllowPlatformChannelAndDefaultModelListing() throws Exception {
        assertAllowed("role-template-view-all-ai-channels", HttpMethod.GET, API_PREFIX + "/aichannels/-/all");
        assertAllowed(
                "role-template-view-all-ai-default-models",
                HttpMethod.GET,
                API_PREFIX + "/aidefaultmodels/-/all");
        assertAllowed(
                "role-template-view-all-ai-catalog",
                HttpMethod.GET,
                API_PREFIX + "/catalogentries");
        assertDenied(
                "role-template-view-all-ai-channels",
                HttpMethod.GET,
                API_PREFIX + "/aidefaultmodels/-/all");
    }

    @Test
    void contentRoleAllowsPlatformWorksTemplatesAndActivities() throws Exception {
        assertAllowed(
                "role-template-view-all-creation-content",
                HttpMethod.GET,
                API_PREFIX + "/works/-/all");
        assertAllowed(
                "role-template-view-all-creation-content",
                HttpMethod.GET,
                API_PREFIX + "/templates/-/all");
        assertAllowed(
                "role-template-view-all-creation-content",
                HttpMethod.GET,
                API_PREFIX + "/activities/-/all");
    }

    @Test
    void contentButtonRolesAllowReviewAndPublishingActions() throws Exception {
        assertAllowed(
                BUTTON_TEMPLATE,
                "role-template-approve-creation-content",
                HttpMethod.POST,
                API_PREFIX + "/works/work-a/approve");
        assertAllowed(
                BUTTON_TEMPLATE,
                "role-template-reject-creation-content",
                HttpMethod.POST,
                API_PREFIX + "/templates/template-a/reject");
        assertAllowed(
                BUTTON_TEMPLATE,
                "role-template-publish-creation-content",
                HttpMethod.POST,
                API_PREFIX + "/activities/activity-a/publish");
        assertAllowed(
                BUTTON_TEMPLATE,
                "role-template-unpublish-creation-content",
                HttpMethod.POST,
                API_PREFIX + "/works/work-a/unpublish");
        assertDenied(
                BUTTON_TEMPLATE,
                "role-template-approve-creation-content",
                HttpMethod.POST,
                API_PREFIX + "/works/work-a/reject");
    }

    @Test
    void sessionRoleAllowsPlatformWideListingAndSummary() throws Exception {
        assertAllowed("role-template-view-all-agent-sessions", HttpMethod.GET, API_PREFIX + "/agentsessions/-/all");
        assertAllowed(
                "role-template-view-all-agent-sessions",
                HttpMethod.GET,
                API_PREFIX + "/agentsessions/-/all-summary");
        assertDenied(
                "role-template-view-all-agent-sessions",
                HttpMethod.GET,
                API_PREFIX + "/generationtasks/-/all");
    }

    @Test
    void sandboxRoleAllowsPlatformWideListing() throws Exception {
        assertAllowed("role-template-view-all-agent-sandboxes", HttpMethod.GET, API_PREFIX + "/sandboxes/-/all");
        assertDenied(
                "role-template-view-all-agent-sandboxes",
                HttpMethod.GET,
                API_PREFIX + "/agentsessions/-/all");
    }

    @Test
    void memoryRoleAllowsPlatformWideJobListing() throws Exception {
        assertAllowed("role-template-view-all-memory-jobs", HttpMethod.GET, API_PREFIX + "/memoryjobs/-/all");
    }

    @Test
    void generationRolesAllowPlatformListingAndForceCancel() throws Exception {
        assertAllowed(
                "role-template-view-all-generation-tasks",
                HttpMethod.GET,
                API_PREFIX + "/generationtasks/-/all");
        assertAllowed(
                "role-template-force-cancel-generation-tasks",
                HttpMethod.POST,
                API_PREFIX + "/generationtasks/task-a/force-cancel");
        assertDenied(
                "role-template-view-all-generation-tasks",
                HttpMethod.POST,
                API_PREFIX + "/generationtasks/task-a/force-cancel");
    }

    @Test
    void creationAssetsRoleAllowsPlatformAssetsAndSubjects() throws Exception {
        assertAllowed("role-template-view-all-creation-assets", HttpMethod.GET, API_PREFIX + "/assets/-/all");
        assertAllowed(
                "role-template-view-all-creation-assets",
                HttpMethod.GET,
                API_PREFIX + "/assets/-/all-facets");
        assertAllowed("role-template-view-all-creation-assets", HttpMethod.GET, API_PREFIX + "/subjects/-/all");
    }

    @Test
    void regularUserRolesStillCannotUseAdministrativeRoutes() throws Exception {
        assertDenied(
                "role-api-template-ai.yaml",
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/-/all");
        assertDenied(
                "role-api-template-ai.yaml",
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aidefaultmodels/-/all");
        assertDenied(
                "role-api-template-creation-content.yaml",
                "role-template-use-index-content",
                HttpMethod.GET,
                API_PREFIX + "/works/-/all");
        assertDenied(
                "role-api-template-creation-content.yaml",
                "role-template-use-index-content",
                HttpMethod.GET,
                API_PREFIX + "/templates/-/all");
        assertDenied(
                "role-api-template-creation-content.yaml",
                "role-template-use-index-content",
                HttpMethod.GET,
                API_PREFIX + "/activities/-/all");
        assertDenied(
                "role-api-template-creation-content.yaml",
                "role-template-use-index-content",
                HttpMethod.POST,
                API_PREFIX + "/works/work-a/approve");
        assertDenied(
                "role-api-template-creation-content.yaml",
                "role-template-use-index-content",
                HttpMethod.POST,
                API_PREFIX + "/templates/template-a/publish");
        assertDenied(
                "role-api-template-creation-content.yaml",
                "role-template-use-index-content",
                HttpMethod.POST,
                API_PREFIX + "/activities/activity-a/unpublish");
        assertDenied(
                AGENT_TEMPLATE,
                "role-template-view-agent-sessions",
                HttpMethod.GET,
                API_PREFIX + "/agentsessions/-/all");
        assertDenied(
                AGENT_TEMPLATE,
                "role-template-view-agent-sandboxes",
                HttpMethod.GET,
                API_PREFIX + "/sandboxes/-/all");
        assertDenied(
                GENERATION_TEMPLATE,
                "role-template-view-generation-tasks",
                HttpMethod.GET,
                API_PREFIX + "/generationtasks/-/all");
        assertDenied(
                GENERATION_TEMPLATE,
                "role-template-manage-generation-tasks",
                HttpMethod.POST,
                API_PREFIX + "/generationtasks/task-a/force-cancel");
    }

    private static void assertAllowed(String roleName, HttpMethod method, String path)
            throws Exception {
        assertAllowed(TEMPLATE, roleName, method, path);
    }

    private static void assertAllowed(
            String template, String roleName, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(template, roleName, method, path))
                .as("expected allow for %s %s in %s", method, path, roleName)
                .isTrue();
    }

    private static void assertDenied(String roleName, HttpMethod method, String path)
            throws Exception {
        assertDenied(TEMPLATE, roleName, method, path);
    }

    private static void assertDenied(
            String template, String roleName, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(template, roleName, method, path))
                .as("expected deny for %s %s in %s", method, path, roleName)
                .isFalse();
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluate(
            String template, String roleName, HttpMethod method, String path) throws Exception {
        var requestInfo =
                RequestInfoFactory.INSTANCE.newRequestInfo(
                        MockServerHttpRequest.method(method, path).build());
        var visitor = new AuthorizingVisitor(new AttributesRecord(requestInfo));
        var role = role(template, roleName);
        var rules = (List<Map<String, Object>>) role.get("rules");
        if (rules == null) {
            return false;
        }
        for (var rule : rules) {
            visitor.visit(template, policyRule(rule), null);
        }
        return visitor.isAllowed();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> role(String template, String roleName) throws Exception {
        var directory = template.startsWith("role-button-") ? "button" : "api";
        try (InputStream input =
                new ClassPathResource("extensions/role/" + directory + "/" + template).getInputStream()) {
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
