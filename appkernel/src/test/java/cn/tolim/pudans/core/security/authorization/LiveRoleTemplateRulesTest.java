package cn.tolim.pudans.core.security.authorization;

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

class LiveRoleTemplateRulesTest {

    private static final String API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

    private static final String SOURCE_TEMPLATE = "role-api-template-live-source.yaml";

    private static final String RECORDING_TEMPLATE = "role-api-template-live-recording.yaml";

    @Test
    void sourceCreationAndPreviewResolveRequireNonResourceRules() throws Exception {
        assertAllowed(SOURCE_TEMPLATE, "role-template-manage-live-sources", HttpMethod.POST, API_PREFIX + "/livesources/from-url");
        assertAllowed(SOURCE_TEMPLATE, "role-template-manage-live-sources", HttpMethod.POST, API_PREFIX + "/livesources/resolve");
        assertDenied(SOURCE_TEMPLATE, "role-template-view-live-sources", HttpMethod.POST, API_PREFIX + "/livesources/from-url");
    }

    @Test
    void sourceViewCoversSummaryAndHealth() throws Exception {
        assertAllowed(SOURCE_TEMPLATE, "role-template-view-live-sources", HttpMethod.GET, API_PREFIX + "/livesources/summary");
        assertAllowed(SOURCE_TEMPLATE, "role-template-view-live-sources", HttpMethod.GET, API_PREFIX + "/livesources/douyin-1/health");
    }

    @Test
    void sourceManageCoversDeleteAndSubresourceActions() throws Exception {
        assertAllowed(SOURCE_TEMPLATE, "role-template-manage-live-sources", HttpMethod.DELETE, API_PREFIX + "/livesources/douyin-1");
        assertAllowed(SOURCE_TEMPLATE, "role-template-manage-live-sources", HttpMethod.POST, API_PREFIX + "/livesources/douyin-1/resolve");
        assertAllowed(SOURCE_TEMPLATE, "role-template-manage-live-sources", HttpMethod.POST, API_PREFIX + "/livesources/douyin-1/record");
    }

    @Test
    void recordingManageCoversStopDownloadRetryAndBatchDelete() throws Exception {
        assertAllowed(RECORDING_TEMPLATE, "role-template-manage-live-recordings", HttpMethod.POST, API_PREFIX + "/liverecordings/rec-1/stop");
        assertAllowed(RECORDING_TEMPLATE, "role-template-manage-live-recordings", HttpMethod.POST, API_PREFIX + "/liverecordings/rec-1/download-url");
        assertAllowed(RECORDING_TEMPLATE, "role-template-manage-live-recordings", HttpMethod.POST, API_PREFIX + "/liverecordings/rec-1/retry");
        assertAllowed(RECORDING_TEMPLATE, "role-template-manage-live-recordings", HttpMethod.DELETE, API_PREFIX + "/liverecordings/batch");
        assertDenied(RECORDING_TEMPLATE, "role-template-view-live-recordings", HttpMethod.DELETE, API_PREFIX + "/liverecordings/batch");
    }

    @Test
    void recordingViewCoversQueryAndActive() throws Exception {
        assertAllowed(RECORDING_TEMPLATE, "role-template-view-live-recordings", HttpMethod.GET, API_PREFIX + "/liverecordings/query");
        assertAllowed(RECORDING_TEMPLATE, "role-template-view-live-recordings", HttpMethod.GET, API_PREFIX + "/liverecordings/active");
    }

    private static void assertAllowed(String template, String roleName, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(template, roleName, method, path))
                .as("expected allow for %s %s in %s", method, path, roleName)
                .isTrue();
    }

    private static void assertDenied(String template, String roleName, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(template, roleName, method, path))
                .as("expected deny for %s %s in %s", method, path, roleName)
                .isFalse();
    }

    private static boolean evaluate(String template, String roleName, HttpMethod method, String path) throws Exception {
        var requestInfo =
                RequestInfoFactory.INSTANCE.newRequestInfo(MockServerHttpRequest.method(method, path).build());
        var visitor = new AuthorizingVisitor(new AttributesRecord(requestInfo));
        for (var rule : rules(template, roleName)) {
            visitor.visit(template, rule, null);
        }
        return visitor.isAllowed();
    }

    @SuppressWarnings("unchecked")
    private static List<Role.PolicyRule> rules(String template, String roleName) throws Exception {
        try (InputStream input = new ClassPathResource("extensions/role/api/" + template).getInputStream()) {
            var result = new ArrayList<Role.PolicyRule>();
            for (var document : new Yaml().loadAll(input)) {
                var role = (Map<String, Object>) document;
                var metadata = (Map<String, Object>) role.get("metadata");
                if (!roleName.equals(metadata.get("name"))) {
                    continue;
                }
                var rules = (List<Map<String, Object>>) role.get("rules");
                if (rules == null) {
                    continue;
                }
                rules.stream().map(LiveRoleTemplateRulesTest::policyRule).forEach(result::add);
            }
            return result;
        }
    }

    private static Role.PolicyRule policyRule(Map<String, Object> rule) {
        return new Role.PolicyRule.Builder()
                .apiGroups(strings(rule.get("apiGroups")))
                .resources(strings(rule.get("resources")))
                .resourceNames(strings(rule.get("resourceNames")))
                .nonResourceURLs(strings(rule.get("nonResourceURLs")))
                .verbs(strings(rule.get("verbs")))
                .build();
    }

    @SuppressWarnings("unchecked")
    private static String[] strings(Object value) {
        return value == null ? new String[] {} : ((List<String>) value).toArray(String[]::new);
    }
}
