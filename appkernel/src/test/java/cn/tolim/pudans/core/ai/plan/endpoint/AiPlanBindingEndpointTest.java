package cn.tolim.pudans.core.ai.plan.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiPlanBinding;
import cn.tolim.pudans.core.ai.plan.PlanBindings;
import cn.tolim.pudans.core.ai.plan.PlanResolution;
import cn.tolim.pudans.infra.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiPlanBindingEndpointTest {

    @Mock
    PlanBindings planBindings;

    @Mock
    PlanResolution planResolution;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiPlanBindingEndpoint(planBindings, planResolution);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void appliesABulkBinding() {
        var request = new PlanBindings.BulkBindRequest(
                "standard",
                List.of(new PlanBindings.BindingSubject(
                        AiPlanBinding.SubjectKind.UserGroup, "rd")),
                1,
                true);
        when(planBindings.bulk(request))
                .thenReturn(Mono.just(new PlanBindings.BulkBindResult(
                        "standard",
                        List.of(new PlanBindings.BindingSummary(
                                "aiplanbinding-usergroup-rd",
                                "standard",
                                AiPlanBinding.SubjectKind.UserGroup,
                                "rd",
                                1,
                                null,
                                null)))));

        client.post()
                .uri("/aiplanbindings/-/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.plan").isEqualTo("standard")
                .jsonPath("$.bindings[0].name").isEqualTo("aiplanbinding-usergroup-rd")
                .jsonPath("$.bindings[0].kind").isEqualTo("UserGroup")
                .jsonPath("$.bindings[0].priority").isEqualTo(1);

        verify(planBindings).bulk(request);
    }

    @Test
    void reportsAnInvalidBulkRequest() {
        when(planBindings.bulk(any(PlanBindings.BulkBindRequest.class)))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        PlanBindings.CODE_BINDING_INVALID,
                        "批量绑定请求不能为空。")));

        client.post()
                .uri("/aiplanbindings/-/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new PlanBindings.BulkBindRequest("standard", List.of(), 0, true))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void previewsAUsersResolvedPlan() {
        when(planResolution.resolve("uid-alice"))
                .thenReturn(Mono.just(new PlanResolution.ResolvedPlan(
                        "standard", "套餐 standard", "User/uid-alice", null)));

        client.get()
                .uri("/aiplanbindings/-/resolve/uid-alice")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.plan").isEqualTo("standard")
                .jsonPath("$.displayName").isEqualTo("套餐 standard")
                .jsonPath("$.matchedBy").isEqualTo("User/uid-alice");

        verify(planResolution).resolve("uid-alice");
    }
}