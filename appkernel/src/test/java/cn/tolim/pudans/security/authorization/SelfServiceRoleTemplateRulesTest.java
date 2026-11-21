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

class SelfServiceRoleTemplateRulesTest {

    private static final String API_PREFIX = "/apis/api.pudans.tolim.cn/v1alpha1";

    @Test
    void passkeyTemplateCoversRegistrationRoutes() throws Exception {
        var template = "role-api-template-own-passkeys.yaml";
        assertAllowed(template, HttpMethod.POST, API_PREFIX + "/passkeys/registration/options");
        assertAllowed(template, HttpMethod.POST, API_PREFIX + "/passkeys/registration/verify");
        assertAllowed(template, HttpMethod.GET, API_PREFIX + "/passkeys");
    }

    @Test
    void connectionTemplateCoversOidcBindingRoutes() throws Exception {
        var template = "role-api-template-own-connections.yaml";
        assertAllowed(template, HttpMethod.GET, API_PREFIX + "/userconnections");
        assertAllowed(template, HttpMethod.GET, API_PREFIX + "/userconnections/oidc/providers");
        assertAllowed(template, HttpMethod.GET, API_PREFIX + "/userconnections/oidc/feishu/authorize-url");
        assertAllowed(template, HttpMethod.POST, API_PREFIX + "/userconnections/oidc/bind");
        assertAllowed(template, HttpMethod.DELETE, API_PREFIX + "/userconnections/oidc-987");
        assertDenied(template, HttpMethod.GET, API_PREFIX + "/userconnections/oidc/feishu/other-url");
    }

    @Test
    void patTemplateCoversRevocationRoutes() throws Exception {
        var template = "role-api-template-manage-own-pat.yaml";
        assertAllowed(template, HttpMethod.POST, API_PREFIX + "/personalaccesstokens");
        assertAllowed(template, HttpMethod.GET, API_PREFIX + "/personalaccesstokens");
        assertAllowed(template, HttpMethod.GET, API_PREFIX + "/personalaccesstokens/token-a");
        assertAllowed(template, HttpMethod.PUT, API_PREFIX + "/personalaccesstokens/token-a/actions/revocation");
        assertAllowed(template, HttpMethod.PUT, API_PREFIX + "/personalaccesstokens/token-a/actions/restoration");
        assertAllowed(template, HttpMethod.DELETE, API_PREFIX + "/personalaccesstokens/token-a");
    }

    private static void assertAllowed(String template, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(template, method, path))
                .as("expected allow for %s %s in %s", method, path, template)
                .isTrue();
    }

    private static void assertDenied(String template, HttpMethod method, String path) throws Exception {
        assertThat(evaluate(template, method, path))
                .as("expected deny for %s %s in %s", method, path, template)
                .isFalse();
    }

    private static boolean evaluate(String template, HttpMethod method, String path) throws Exception {
        var requestInfo =
                RequestInfoFactory.INSTANCE.newRequestInfo(MockServerHttpRequest.method(method, path).build());
        var visitor = new AuthorizingVisitor(new AttributesRecord(requestInfo));
        for (var rule : rules(template)) {
            visitor.visit(template, rule, null);
        }
        return visitor.isAllowed();
    }

    @SuppressWarnings("unchecked")
    private static List<Role.PolicyRule> rules(String template) throws Exception {
        try (InputStream input = new ClassPathResource("extensions/role/api/" + template).getInputStream()) {
            var document = (Map<String, Object>) new Yaml().load(input);
            var rules = (List<Map<String, Object>>) document.get("rules");
            return rules.stream().map(SelfServiceRoleTemplateRulesTest::policyRule).toList();
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
