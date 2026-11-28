package cn.tolim.pudans.contract.iam;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.iam.dto.IamApiResponse;
import cn.tolim.pudans.contract.iam.dto.IamApiResult;
import cn.tolim.pudans.contract.iam.dto.app.ApplicationAccessResponse;
import cn.tolim.pudans.contract.iam.dto.authz.PolicyEvaluateRequest;
import cn.tolim.pudans.contract.iam.dto.authz.PolicyEvaluateResponse;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateRequest;
import cn.tolim.pudans.contract.iam.dto.session.SessionValidateResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Contract DTO behavior tests
class IamContractDtoTests {

    @Test
    void sessionValidationDtosCarryDecisionFacts() {
        SessionValidateRequest request = SessionValidateRequest.builder()
                .sid("sid-1").appCode("order").build();
        LocalDateTime expiresAt = LocalDateTime.of(2026, 11, 27, 20, 0);
        SessionValidateResponse response = SessionValidateResponse.builder()
                .active(true).sub("user-1").userType("USER").orgId("org-1")
                .appCode("order").expiresAt(expiresAt).build();

        assertThat(request.getSid()).isEqualTo("sid-1");
        assertThat(request.getAppCode()).isEqualTo("order");
        assertThat(response.getActive()).isTrue();
        assertThat(response.getSub()).isEqualTo("user-1");
        assertThat(response.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void ticketExchangeCarriesUserClaims() {
        UserInfo user = UserInfo.builder()
                .userId("user-1").username("alice").userType("USER")
                .roles(List.of("order-admin")).groups(List.of("operations"))
                .attributes(Map.of("mfa", true)).build();
        TicketExchangeResponse response = TicketExchangeResponse.builder()
                .sid("sid-1").appCode("order").userId("user-1").user(user).build();

        assertThat(response.getUser().getRoles()).containsExactly("order-admin");
        assertThat(response.getUser().getAttributes()).containsEntry("mfa", true);
    }

    @Test
    void authorizationDtosUseStringDecisionCodes() {
        PolicyEvaluateRequest request = PolicyEvaluateRequest.builder()
                .principalType("USER").principalId("user-1").appCode("order")
                .action("order:read").resource("order:order/1").build();
        PolicyEvaluateResponse response = PolicyEvaluateResponse.builder()
                .decision("ALLOW").allowed(true).matchedPolicies(List.of("viewer")).build();
        ApplicationAccessResponse access = ApplicationAccessResponse.builder()
                .allowed(true).appCode("order").userId("user-1").build();

        assertThat(request.getAction()).isEqualTo("order:read");
        assertThat(response.getDecision()).isEqualTo("ALLOW");
        assertThat(response.getAllowed()).isTrue();
        assertThat(access.getAppCode()).isEqualTo("order");
    }

    @Test
    void apiResultEnvelopeMatchesIamApiRestResultShape() {
        IamApiResult<ApplicationAccessResponse> result = IamApiResult.ok(
                ApplicationAccessResponse.builder().allowed(true).appCode("order").build());

        assertThat(result.getSuccess()).isTrue();
        assertThat(result.getStatus()).isEqualTo("200");
        assertThat(result.getMessage()).isEqualTo("success");
        assertThat(result.getResult().getAppCode()).isEqualTo("order");
        assertThat(IamApiResult.error("403", "denied").getSuccess()).isFalse();

        IamApiResponse<ApplicationAccessResponse> response = IamApiResponse.ok(
                ApplicationAccessResponse.builder().allowed(true).appCode("order").build());
        assertThat(response.getCode()).isEqualTo("OK");
        assertThat(response.getMessage()).isEqualTo("OK");
        assertThat(response.getData().getAppCode()).isEqualTo("order");
    }
}