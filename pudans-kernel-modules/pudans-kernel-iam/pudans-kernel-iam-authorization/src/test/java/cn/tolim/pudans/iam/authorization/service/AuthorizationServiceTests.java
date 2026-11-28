package cn.tolim.pudans.iam.authorization.service;

import cn.tolim.pudans.iam.authorization.dto.PolicyDocument;
import cn.tolim.pudans.iam.authorization.dto.PolicyEvaluateRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicyEvaluateResponse;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationRequest;
import cn.tolim.pudans.iam.authorization.dto.PolicySimulationResponse;
import cn.tolim.pudans.iam.authorization.entity.AuthzLogEntity;
import cn.tolim.pudans.iam.authorization.entity.AuthzPolicyEntity;
import cn.tolim.pudans.iam.authorization.enums.AuthorizationStatus;
import cn.tolim.pudans.iam.authorization.enums.PolicyDecision;
import cn.tolim.pudans.iam.authorization.enums.PrincipalType;
import cn.tolim.pudans.iam.authorization.repository.AuthzLogRepository;
import cn.tolim.pudans.iam.authorization.repository.AuthzPolicyRepository;
import cn.tolim.pudans.iam.authorization.repository.ServiceActionRepository;
import cn.tolim.pudans.iam.authorization.service.PrincipalPermissionResolver.EffectivePermissions;
import cn.tolim.pudans.iam.authorization.service.PrincipalPermissionResolver.EffectivePolicy;
import cn.tolim.pudans.iam.authorization.service.PrincipalPermissionResolver.PrincipalRef;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTests {

    private static final String ALLOW_READ_DOCUMENT = """
            {"version":"1.0","statement":[{"sid":"AllowRead","effect":"Allow",
            "action":["order:read"],"resource":["order:order/*"]}]}
            """;

    private static final String DENY_EXPORT_DOCUMENT = """
            {"version":"1.0","statement":[{"sid":"DenyExport","effect":"Deny",
            "action":["order:export"],"resource":["*"]}]}
            """;

    @Mock
    private PrincipalPermissionResolver permissionResolver;

    @Mock
    private AuthzPolicyRepository policyRepository;

    @Mock
    private AuthzLogRepository authzLogRepository;

    @Mock
    private AppRepository appRepository;

    @Mock
    private ServiceActionRepository serviceActionRepository;

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        PolicyDocumentService documentService = new PolicyDocumentService(objectMapper,
                appRepository, serviceActionRepository);
        authorizationService = new AuthorizationService(permissionResolver,
                new PolicyEvaluationEngine(), documentService, policyRepository, authzLogRepository,
                new AuthzDecisionCache(), objectMapper);
    }

    @Test
    void groupPolicyAllowsAccessAndDecisionIsAudited() {
        when(permissionResolver.resolve(PrincipalType.USER, "user-1", "order", "order:order/1"))
                .thenReturn(effective(new EffectivePolicy(policy("GroupPolicy",
                        AuthorizationStatus.ENABLED, ALLOW_READ_DOCUMENT), "*",
                        new PrincipalRef(PrincipalType.USER_GROUP, "group-1"))));

        PolicyEvaluateResponse response = authorizationService.evaluate(request("order:read",
                "order:order/1"));

        assertThat(response.getDecision()).isEqualTo("ALLOW");
        assertThat(response.getAllowed()).isTrue();
        assertThat(response.getMatchedPolicies()).contains("GroupPolicy");
        AuthzLogEntity audit = captureAudit();
        assertThat(audit.getDecision()).isEqualTo(PolicyDecision.ALLOW);
        assertThat(audit.getPrincipalId()).isEqualTo("user-1");
        assertThat(audit.getAppCode()).isEqualTo("order");
        assertThat(audit.getAction()).isEqualTo("order:read");
    }

    @Test
    void rolePolicyExplicitDenyWinsAndDecisionIsAudited() {
        when(permissionResolver.resolve(PrincipalType.USER, "user-1", "order", "order:order/1"))
                .thenReturn(effective(
                        new EffectivePolicy(policy("AllowPolicy", AuthorizationStatus.ENABLED,
                                ALLOW_READ_DOCUMENT), "*",
                                new PrincipalRef(PrincipalType.USER, "user-1")),
                        new EffectivePolicy(policy("DenyPolicy", AuthorizationStatus.ENABLED,
                                DENY_EXPORT_DOCUMENT), "*",
                                new PrincipalRef(PrincipalType.ROLE, "role-1"))));

        PolicyEvaluateResponse response = authorizationService.evaluate(request("order:export",
                "order:order/1"));

        assertThat(response.getDecision()).isEqualTo("DENY");
        assertThat(response.getAllowed()).isFalse();
        assertThat(response.getReason()).contains("Explicit deny");
        AuthzLogEntity audit = captureAudit();
        assertThat(audit.getDecision()).isEqualTo(PolicyDecision.DENY);
        assertThat(audit.getReason()).contains("Explicit deny");
    }

    @Test
    void defaultDenyWhenPrincipalHasNoPoliciesAndDecisionIsAudited() {
        when(permissionResolver.resolve(PrincipalType.USER, "user-1", "order", "order:order/1"))
                .thenReturn(effective());

        PolicyEvaluateResponse response = authorizationService.evaluate(request("order:read",
                "order:order/1"));

        assertThat(response.getDecision()).isEqualTo("DENY");
        assertThat(response.getReason()).contains("Default deny");
        assertThat(captureAudit().getDecision()).isEqualTo(PolicyDecision.DENY);
    }

    @Test
    void invalidActionFormatIsRejectedBeforeEvaluation() {
        assertThatThrownBy(() -> authorizationService.evaluate(request("billing:read",
                "order:order/1")))
                .hasMessageContaining("must be in {appCode}:{action} format");

        verifyNoInteractions(permissionResolver, authzLogRepository);
    }

    @Test
    void cachedDecisionIsReusedButEveryDecisionIsStillAudited() {
        when(permissionResolver.resolve(PrincipalType.USER, "user-1", "order", "order:order/1"))
                .thenReturn(effective(new EffectivePolicy(policy("GroupPolicy",
                        AuthorizationStatus.ENABLED, ALLOW_READ_DOCUMENT), "*",
                        new PrincipalRef(PrincipalType.USER_GROUP, "group-1"))));

        PolicyEvaluateResponse first = authorizationService.evaluate(request("order:read",
                "order:order/1"));
        PolicyEvaluateResponse second = authorizationService.evaluate(request("order:read",
                "order:order/1"));

        assertThat(first.getDecision()).isEqualTo("ALLOW");
        assertThat(second.getDecision()).isEqualTo("ALLOW");
        verify(permissionResolver, times(1)).resolve(PrincipalType.USER, "user-1", "order",
                "order:order/1");
        verify(authzLogRepository, times(2)).save(any(AuthzLogEntity.class));
    }

    @Test
    void simulatorUsesInlinePoliciesAndDoesNotAudit() {
        PolicyDocument document = new PolicyDocument();
        PolicyDocument.Statement statement = new PolicyDocument.Statement();
        statement.setSid("SimAllow");
        statement.setEffect("Allow");
        statement.setAction(List.of("order:read"));
        statement.setResource(List.of("order:order/*"));
        document.setStatement(List.of(statement));
        PolicySimulationRequest request = new PolicySimulationRequest();
        request.setPolicies(List.of(document));
        request.setAppCode("order");
        request.setAction("order:read");
        request.setResource("order:order/1");

        PolicySimulationResponse response = authorizationService.simulate(request);

        assertThat(response.getDecision()).isEqualTo("ALLOW");
        assertThat(response.getAllowed()).isTrue();
        assertThat(response.getMatchedPolicies()).containsExactly("inline[0]");
        verifyNoInteractions(permissionResolver, authzLogRepository);
    }

    @Test
    void simulatorDefaultsToDenyForUnmatchedInlinePolicy() {
        PolicyDocument document = new PolicyDocument();
        PolicyDocument.Statement statement = new PolicyDocument.Statement();
        statement.setSid("SimAllow");
        statement.setEffect("Allow");
        statement.setAction(List.of("order:read"));
        statement.setResource(List.of("order:order/1"));
        document.setStatement(List.of(statement));
        PolicySimulationRequest request = new PolicySimulationRequest();
        request.setPolicies(List.of(document));
        request.setAppCode("order");
        request.setAction("order:read");
        request.setResource("order:order/2");

        PolicySimulationResponse response = authorizationService.simulate(request);

        assertThat(response.getDecision()).isEqualTo("DENY");
        assertThat(response.getReason()).contains("Default deny");
    }

    @Test
    void conditionContextIsEvaluated() {
        String conditionalAllow = """
                {"version":"1.0","statement":[{"sid":"RequireMfa","effect":"Allow",
                "action":["order:read"],"resource":["*"],"condition":{"mfa":true}}]}
                """;
        when(permissionResolver.resolve(PrincipalType.USER, "user-1", "order", "order:order/1"))
                .thenReturn(effective(new EffectivePolicy(policy("MfaPolicy",
                        AuthorizationStatus.ENABLED, conditionalAllow), "*",
                        new PrincipalRef(PrincipalType.USER, "user-1"))));
        PolicyEvaluateRequest request = request("order:read", "order:order/1");
        request.setContext(Map.of("mfa", false));

        PolicyEvaluateResponse response = authorizationService.evaluate(request);

        assertThat(response.getDecision()).isEqualTo("DENY");
        assertThat(response.getReason()).contains("Default deny");
    }

    private static PolicyEvaluateRequest request(String action, String resource) {
        PolicyEvaluateRequest request = new PolicyEvaluateRequest();
        request.setPrincipalType("USER");
        request.setPrincipalId("user-1");
        request.setAppCode("order");
        request.setAction(action);
        request.setResource(resource);
        return request;
    }

    private AuthzLogEntity captureAudit() {
        ArgumentCaptor<AuthzLogEntity> captor = ArgumentCaptor.forClass(AuthzLogEntity.class);
        verify(authzLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static AuthzPolicyEntity policy(String code, AuthorizationStatus status,
                                            String document) {
        AuthzPolicyEntity policy = new AuthzPolicyEntity();
        policy.setCode(code);
        policy.setName(code);
        policy.setStatus(status);
        policy.setDocument(document);
        return policy;
    }

    private static EffectivePermissions effective(EffectivePolicy... policies) {
        return new EffectivePermissions(List.of(), List.of(), List.of(policies));
    }
}