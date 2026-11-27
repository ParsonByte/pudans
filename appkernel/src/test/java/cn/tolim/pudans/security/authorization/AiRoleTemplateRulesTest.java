package cn.tolim.pudans.security.authorization;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.rbac.Role;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.yaml.snakeyaml.Yaml;

class AiRoleTemplateRulesTest {

    private static final String API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

    private static final String TEMPLATE = "role-api-template-ai.yaml";

    @Test
    void userCatalogRoleCoversEveryDynamicCatalog() throws Exception {
        var role = "role-template-view-ai-catalog";
        for (var catalog : List.of(
                "models",
                "creation",
                "chat",
                "tools",
                "skills",
                "assets",
                "understanding",
                "memory",
                "sandbox",
                "explore")) {
            assertAllowed(role, HttpMethod.GET, API_PREFIX + "/catalogs/-/" + catalog);
        }
        assertDenied(role, HttpMethod.GET, API_PREFIX + "/catalogs/-/unknown");
    }

    @Test
    void userQuotaRoleCoversOnlyTheOwnSnapshot() throws Exception {
        var role = "role-template-view-ai-quota";
        assertAllowed(role, HttpMethod.GET, API_PREFIX + "/quotas/-/snapshot");
        assertDenied(role, HttpMethod.GET, API_PREFIX + "/aiquotaplans");
        assertDenied(role, HttpMethod.GET, API_PREFIX + "/aiquotaassignments");
    }

    @Test
    void aiConfigManagementCoversCrudAndChannelTests() throws Exception {
        var role = "role-template-manage-ai-config";
        assertAllowed(role, HttpMethod.POST, API_PREFIX + "/aichannels");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels");
        assertAllowed(role, HttpMethod.PATCH, API_PREFIX + "/aimodels/model-1");
        assertAllowed(role, HttpMethod.DELETE, API_PREFIX + "/aichannelmodels/model-1");
        assertAllowed(role, HttpMethod.PUT, API_PREFIX + "/aidefaultmodels/chat");
        assertAllowed(role, HttpMethod.POST, API_PREFIX + "/aichannels/channel-1/-/test");
        assertAllowed(role, HttpMethod.POST, API_PREFIX + "/aichannels/channel-1/-/models");
        for (var operation : List.of(
                "files",
                "anthropic-files",
                "search",
                "fetch",
                "ocr",
                "tts",
                "asr",
                "fim",
                "chat-completions",
                "messages",
                "responses")) {
            assertAllowed(
                    role,
                    HttpMethod.POST,
                    API_PREFIX + "/aichannels/channel-1/-/" + operation);
        }
        assertAllowed(
                role,
                HttpMethod.DELETE,
                API_PREFIX + "/aichannels/channel-1/-/files/file-1");
        assertAllowed(
                role,
                HttpMethod.POST,
                API_PREFIX + "/aichannels/channel-1/-/minimax/image-generations");
        assertAllowed(
                role,
                HttpMethod.POST,
                API_PREFIX + "/aichannels/channel-1/-/minimax/video-generations");
        assertAllowed(
                role,
                HttpMethod.DELETE,
                API_PREFIX + "/aichannels/channel-1/-/minimax/video-generations/task-1");
        assertAllowed(
                role,
                HttpMethod.POST,
                API_PREFIX + "/aichannels/channel-1/-/minimax/voice-list");
        assertAllowed(
                role,
                HttpMethod.POST,
                API_PREFIX + "/aichannels/channel-1/-/minimax/tts-websocket");
        assertAllowed(
                role,
                HttpMethod.DELETE,
                API_PREFIX + "/aichannels/channel-1/-/anthropic-files/file-1");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/models");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/files");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/files/file-1");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/anthropic-files");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/balance");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/files/file-1/content");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/minimax/video-generations");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/minimax/video-generations/task-1");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aichannels/channel-1/-/minimax/tts-websocket");
        assertDenied(
                "role-template-view-ai-config",
                HttpMethod.POST,
                API_PREFIX + "/aichannels/channel-1/-/minimax/tts-websocket");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aiproviders");
        assertAllowed(
                "role-template-view-ai-config",
                HttpMethod.GET,
                API_PREFIX + "/aiprotocols");
        assertDenied(role, HttpMethod.POST, API_PREFIX + "/aiquotaplans");
    }

    @Test
    void quotaAndCatalogManagementCoverTheirOwnRoutes() throws Exception {
        assertAllowed(
                "role-template-manage-ai-quota",
                HttpMethod.POST,
                API_PREFIX + "/aiquotaplans");
        assertAllowed(
                "role-template-manage-ai-quota",
                HttpMethod.GET,
                API_PREFIX + "/aiquotaassignments/-/resolve");
        assertAllowed(
                "role-template-manage-ai-catalogs",
                HttpMethod.POST,
                API_PREFIX + "/catalogentries");
        assertAllowed(
                "role-template-manage-ai-catalogs",
                HttpMethod.PATCH,
                API_PREFIX + "/catalogentries/00000000-0000-0000-0000-000000000001");
        assertAllowed(
                "role-template-manage-ai-catalogs",
                HttpMethod.DELETE,
                API_PREFIX + "/catalogentries/00000000-0000-0000-0000-000000000001");
        assertDenied(
                "role-template-manage-ai-quota",
                HttpMethod.POST,
                API_PREFIX + "/catalogentries");
        assertDenied(
                "role-template-view-ai-catalog",
                HttpMethod.DELETE,
                API_PREFIX + "/catalogentries/00000000-0000-0000-0000-000000000001");
    }

    @Test
    void everyAiTopLevelRouteIsCoveredByARoleRule() throws Exception {
        var covered = new TreeSet<String>();
        for (var role : roles()) {
            var rules = (List<Map<String, Object>>) role.get("rules");
            if (rules == null) {
                continue;
            }
            for (var rule : rules) {
                for (var resource : strings(rule.get("resources"))) {
                    covered.add(resource.split("/")[0]);
                }
            }
        }

        assertThat(covered).contains(
                "aichannels",
                "aimodels",
                "aichannelmodels",
                "aipricerules",
                "aidefaultmodels",
                "aiproviders",
                "aiprotocols",
                "aiquotaplans",
                "aiquotaassignments",
                "quotas",
                "catalogentries",
                "catalogs");
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

    private static boolean evaluate(String roleName, HttpMethod method, String path)
            throws Exception {
        var requestInfo =
                RequestInfoFactory.INSTANCE.newRequestInfo(
                        MockServerHttpRequest.method(method, path).build());
        var visitor = new AuthorizingVisitor(new AttributesRecord(requestInfo));
        for (var role : roles()) {
            var metadata = (Map<String, Object>) role.get("metadata");
            if (!roleName.equals(metadata.get("name"))) {
                continue;
            }
            var rules = (List<Map<String, Object>>) role.get("rules");
            if (rules == null) {
                continue;
            }
            for (var rule : rules) {
                visitor.visit(TEMPLATE, policyRule(rule), null);
            }
        }
        return visitor.isAllowed();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> roles() throws Exception {
        try (InputStream input =
                new ClassPathResource("extensions/role/api/" + TEMPLATE).getInputStream()) {
            var result = new ArrayList<Map<String, Object>>();
            for (var document : new Yaml().loadAll(input)) {
                if (document instanceof Map<?, ?> role) {
                    result.add((Map<String, Object>) role);
                }
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
