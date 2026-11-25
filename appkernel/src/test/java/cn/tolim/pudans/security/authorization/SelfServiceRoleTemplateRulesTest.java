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

class SelfServiceRoleTemplateRulesTest {

    private static final String API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

    private static final String IDENTITY_TEMPLATE = "role-api-template-identity.yaml";

    private static final String CREATION_CONTENT_TEMPLATE =
            "role-api-template-creation-content.yaml";

    private static final String AGENT_TEMPLATE = "role-api-template-agent.yaml";

    @Test
    void passkeyTemplateCoversRegistrationRoutes() throws Exception {
        var roleName = "role-template-own-passkeys";
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/passkeys/registration/options");
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/passkeys/registration/verify");
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/passkeys");
    }

    @Test
    void connectionTemplateCoversOidcBindingRoutes() throws Exception {
        var roleName = "role-template-own-connections";
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/userconnections");
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/userconnections/oidc/providers");
        assertAllowed(
                IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/userconnections/oidc/feishu/authorize-url");
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/userconnections/oidc/bind");
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.DELETE, API_PREFIX + "/userconnections/oidc-987");
        assertDenied(IDENTITY_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/userconnections");
        assertDenied(IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/userconnections/oidc-987");
        assertDenied(IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/userconnections/oidc/feishu/other-url");
    }

    @Test
    void patTemplateCoversRevocationRoutes() throws Exception {
        var roleName = "role-template-manage-own-pat";
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/personalaccesstokens");
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/personalaccesstokens");
        assertAllowed(IDENTITY_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/personalaccesstokens/token-a");
        assertAllowed(
                IDENTITY_TEMPLATE,
                roleName,
                HttpMethod.PUT,
                API_PREFIX + "/personalaccesstokens/token-a/actions/revocation");
        assertAllowed(
                IDENTITY_TEMPLATE,
                roleName,
                HttpMethod.PUT,
                API_PREFIX + "/personalaccesstokens/token-a/actions/restoration");
        assertAllowed(
                IDENTITY_TEMPLATE, roleName, HttpMethod.DELETE, API_PREFIX + "/personalaccesstokens/token-a");
    }

    @Test
    void assetTemplateCoversAssetArchiveAndCatalogRoutes() throws Exception {
        var roleName = "role-template-use-index-assets";
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/assets/-/facets");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/assets/-/items");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/assets/asset-a/view");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/catalogs/-/assets");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/assets/-/register");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/assets/asset-a/-/reuse");
        assertDenied(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/assets/-/items");
    }

    @Test
    void contentTemplateCoversExploreContentAndCreatorRoutes() throws Exception {
        var roleName = "role-template-use-index-content";
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/catalogs/-/explore");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/explore/-/feed");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/works/work-a/view");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/works/work-a/play");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/works/work-a/-/like");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/works/work-a/-/remix");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/templates/-/feed");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/templates/template-a/view");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/activities/-/feed");
        assertAllowed(
                CREATION_CONTENT_TEMPLATE,
                roleName,
                HttpMethod.POST,
                API_PREFIX + "/activities/activity-a/submissions");
        assertAllowed(
                CREATION_CONTENT_TEMPLATE,
                roleName,
                HttpMethod.GET,
                API_PREFIX + "/activities/activity-a/submissions/-/mine");
        assertAllowed(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/subjects/-/mine");
        assertAllowed(
                CREATION_CONTENT_TEMPLATE,
                roleName,
                HttpMethod.POST,
                API_PREFIX + "/creators/creator-a/-/follow");
        assertDenied(CREATION_CONTENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/works/work-a/-/like");
    }

    @Test
    void agentViewTemplateCoversRunStatusAndEventStreams() throws Exception {
        var roleName = "role-template-view-agent-sessions";
        assertAllowed(AGENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/agentruns/run-a/status");
        assertAllowed(AGENT_TEMPLATE, roleName, HttpMethod.GET, API_PREFIX + "/agentruns/run-a/events");
        assertDenied(AGENT_TEMPLATE, roleName, HttpMethod.POST, API_PREFIX + "/agentruns/-/start");
    }

    private static void assertAllowed(String template, String roleName, HttpMethod method, String path)
            throws Exception {
        assertThat(evaluate(template, roleName, method, path))
                .as("expected allow for %s %s in %s", method, path, roleName)
                .isTrue();
    }

    private static void assertDenied(String template, String roleName, HttpMethod method, String path)
            throws Exception {
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
                rules.stream().map(SelfServiceRoleTemplateRulesTest::policyRule).forEach(result::add);
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
