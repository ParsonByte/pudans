package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.dto.PolicyDocument;
import cn.tolim.pudans.iam.authorization.enums.PolicyDecision;
import cn.tolim.pudans.iam.authorization.service.PolicyEvaluationEngine.CandidatePolicy;
import cn.tolim.pudans.iam.authorization.service.PolicyEvaluationEngine.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;


class PolicyEvaluationEngineTests {

    private final PolicyEvaluationEngine engine = new PolicyEvaluationEngine();

    @Test
    void explicitDenyWinsOverAllow() {
        CandidatePolicy allow = policy("AllowPolicy", statement("AllowRead", "Allow",
                List.of("order:read", "order:export"), List.of("*"), null));
        CandidatePolicy deny = policy("DenyPolicy", statement("DenyExport", "Deny",
                List.of("order:export"), List.of("*"), null));

        Result read = engine.evaluate(List.of(allow, deny), "order", "order:read",
                "order:order/1", null, null);
        Result export = engine.evaluate(List.of(allow, deny), "order", "order:export",
                "order:order/1", null, null);

        assertThat(read.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(export.decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(export.reason()).contains("Explicit deny");
        assertThat(export.matchedPolicies()).contains("DenyPolicy");
    }

    @Test
    void defaultDenyWhenNoStatementMatches() {
        CandidatePolicy allow = policy("AllowPolicy", statement("AllowRead", "Allow",
                List.of("order:read"), List.of("order:order/*"), null));

        Result result = engine.evaluate(List.of(allow), "order", "order:write",
                "order:order/1", null, null);

        assertThat(result.decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("Default deny");
        assertThat(result.matchedPolicies()).isEmpty();
    }

    @Test
    void defaultDenyWhenThereAreNoPolicies() {
        Result result = engine.evaluate(List.of(), "order", "order:read", "order:order/1",
                null, null);

        assertThat(result.decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(result.reason()).contains("Default deny");
    }

    @Test
    void resourceMismatchDeniesAccess() {
        CandidatePolicy allow = policy("AllowRead", statement("AllowSpecificOrder", "Allow",
                List.of("order:read"), List.of("order:order/1"), null));

        Result allowed = engine.evaluate(List.of(allow), "order", "order:read",
                "order:order/1", null, null);
        Result denied = engine.evaluate(List.of(allow), "order", "order:read",
                "order:order/2", null, null);

        assertThat(allowed.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(denied.decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(denied.reason()).contains("Default deny");
    }

    @Test
    void denyStatementWithNonMatchingResourceDoesNotDeny() {
        CandidatePolicy allow = policy("AllowRead", statement("AllowAllOrders", "Allow",
                List.of("order:read"), List.of("*"), null));
        CandidatePolicy deny = policy("DenySecret", statement("DenySecretOrder", "Deny",
                List.of("order:read"), List.of("order:order/secret"), null));

        Result result = engine.evaluate(List.of(allow, deny), "order", "order:read",
                "order:order/public", null, null);

        assertThat(result.decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @Test
    void wildcardActionMatchesConcreteAction() {
        CandidatePolicy allow = policy("AllowAllOrderActions", statement("AllowAll", "Allow",
                List.of("order:*"), List.of("order:order/*"), null));

        Result result = engine.evaluate(List.of(allow), "order", "order:read",
                "order:order/1", null, null);

        assertThat(result.decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @Test
    void conditionMismatchFailsClosed() {
        CandidatePolicy allow = policy("AllowWithMfa", statement("RequireMfa", "Allow",
                List.of("order:read"), List.of("order:order/*"), Map.of("mfa", true)));

        Result withoutMfa = engine.evaluate(List.of(allow), "order", "order:read",
                "order:order/1", null, Map.of());
        Result withMfa = engine.evaluate(List.of(allow), "order", "order:read",
                "order:order/1", null, Map.of("mfa", true));

        assertThat(withoutMfa.decision()).isEqualTo(PolicyDecision.DENY);
        assertThat(withMfa.decision()).isEqualTo(PolicyDecision.ALLOW);
    }

    @Test
    void organizationVariableResourceMatches() {
        CandidatePolicy allow = policy("AllowOrgOrders", statement("AllowOrg", "Allow",
                List.of("order:read"), List.of("org/${orgId}/order/*"), null));

        Result sameOrg = engine.evaluate(List.of(allow), "order", "order:read",
                "org/org-1/order/1", "org-1", null);
        Result otherOrg = engine.evaluate(List.of(allow), "order", "order:read",
                "org/org-1/order/1", "org-2", null);

        assertThat(sameOrg.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(otherOrg.decision()).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void multipleAllowPoliciesFromGroupsAndRolesCombine() {
        CandidatePolicy groupPolicy = policy("GroupPolicy", statement("GroupAllow", "Allow",
                List.of("order:read"), List.of("*"), null));
        CandidatePolicy rolePolicy = policy("RolePolicy", statement("RoleAllow", "Allow",
                List.of("order:list"), List.of("*"), null));

        Result read = engine.evaluate(List.of(groupPolicy, rolePolicy), "order", "order:read",
                "order:order/1", null, null);
        Result list = engine.evaluate(List.of(groupPolicy, rolePolicy), "order", "order:list",
                "order:order/1", null, null);

        assertThat(read.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(list.decision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(list.matchedPolicies()).contains("RolePolicy");
    }

    private static CandidatePolicy policy(String code, PolicyDocument.Statement statement) {
        PolicyDocument document = new PolicyDocument();
        document.setVersion("1.0");
        document.setStatement(List.of(statement));
        return new CandidatePolicy(code, document);
    }

    private static PolicyDocument.Statement statement(String sid, String effect,
                                                      List<String> actions,
                                                      List<String> resources,
                                                      Map<String, Object> condition) {
        PolicyDocument.Statement statement = new PolicyDocument.Statement();
        statement.setSid(sid);
        statement.setEffect(effect);
        statement.setAction(actions);
        statement.setResource(resources);
        statement.setCondition(condition);
        return statement;
    }
}