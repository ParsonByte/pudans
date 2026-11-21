package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolCodecRegistry;
import cn.tolim.pudans.core.ai.gateway.protocol.AiResponseEncoders;
import cn.tolim.pudans.core.ai.gateway.protocol.GeminiGatewayCodec;
import cn.tolim.pudans.core.ai.plan.PlanQuotas;
import cn.tolim.pudans.core.ai.plan.PlanUsage;
import cn.tolim.pudans.core.ai.plan.PlanUsage.PlanSummary;
import cn.tolim.pudans.core.ai.plan.PlanUsage.UsageView;
import cn.tolim.pudans.core.ai.plan.PlanUsage.WindowUsage;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiGatewayEndpointTest {

    @Mock
    AiGateway aiGateway;

    @Mock
    AiEndpointRequests requests;

    @Mock
    GatewayHealth gatewayHealth;

    @Mock
    PlanUsage planUsage;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        lenient().when(requests.userId(any(), anyString(), anyString())).thenReturn(Mono.just("user-1"));
        lenient().when(planUsage.usage("user-1")).thenReturn(Mono.empty());
        var endpoint = new AiGatewayEndpoint(
                new AiProtocolCodecRegistry(mapper),
                aiGateway,
                new AiResponseEncoders(mapper, new GeminiGatewayCodec(mapper)),
                new GeminiGatewayCodec(mapper),
                gatewayHealth,
                requests,
                planUsage);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(principal())).build()))
                .build();
    }

    @Test
    void completesAnOpenAiChatRequest() {
        when(aiGateway.complete(any())).thenReturn(Mono.just(result()));

        client.post()
                .uri("/aigateway/-/openai/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-test","messages":[{"role":"user","content":"hi"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.choices[0].message.content").isEqualTo("pong")
                .jsonPath("$.usage.total_tokens").isEqualTo(3);
    }

    @Test
    void streamsAnAnthropicMessagesRequest() {
        when(aiGateway.stream(any())).thenReturn(Flux.just(
                new AiGatewayEvent.Meta(meta()),
                new AiGatewayEvent.Delta(new AiStreamEvent("pong", null, null, null, null)),
                new AiGatewayEvent.Usage(new AiTokenUsage(1, 2, null, null), BigDecimal.ONE, "PROVIDER"),
                new AiGatewayEvent.Done("msg-1", "stop", false)));

        client.post()
                .uri("/aigateway/-/anthropic/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"claude-test","max_tokens":64,"stream":true,
                         "messages":[{"role":"user","content":"hi"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: message_start");
                    org.assertj.core.api.Assertions.assertThat(body).contains("text_delta");
                    org.assertj.core.api.Assertions.assertThat(body).contains("message_stop");
                });
    }

    @Test
    void completesAGeminiGenerateContentRequest() {
        when(aiGateway.complete(any())).thenReturn(Mono.just(result()));

        client.post()
                .uri("/aigateway/-/gemini/v1beta/models/gemini-test:generateContent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"contents":[{"role":"user","parts":[{"text":"hi"}]}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.candidates[0].content.role").isEqualTo("model")
                .jsonPath("$.candidates[0].content.parts[0].text").isEqualTo("pong")
                .jsonPath("$.candidates[0].finishReason").isEqualTo("STOP")
                .jsonPath("$.usageMetadata.totalTokenCount").isEqualTo(3);
    }

    @Test
    void streamsAGeminiGenerateContentRequest() {
        when(aiGateway.stream(any())).thenReturn(Flux.just(
                new AiGatewayEvent.Meta(meta()),
                new AiGatewayEvent.Delta(new AiStreamEvent("pong", null, null, null, null)),
                new AiGatewayEvent.Usage(new AiTokenUsage(1, 2, null, null), BigDecimal.ONE, "PROVIDER"),
                new AiGatewayEvent.Done("msg-1", "stop", false)));

        client.post()
                .uri("/aigateway/-/gemini/v1beta/models/gemini-test:streamGenerateContent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"contents":[{"role":"user","parts":[{"text":"hi"}]}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body).contains("data: ");
                    org.assertj.core.api.Assertions.assertThat(body).contains("\"text\":\"pong\"");
                    org.assertj.core.api.Assertions.assertThat(body).contains("\"finishReason\":\"STOP\"");
                    org.assertj.core.api.Assertions.assertThat(body).contains("\"totalTokenCount\":3");
                });
    }

    @Test
    void exposesGatewayStatus() {
        when(gatewayHealth.status()).thenReturn(Mono.just(new GatewayHealth.Status(
                true,
                "http://localhost/aigateway",
                List.of(AiProvider.Protocol.OPENAI_CHAT, AiProvider.Protocol.GEMINI),
                List.of("gpt-test"),
                List.of("fast-group"),
                "fast-group",
                true,
                2,
                "1.0.0")));

        client.get()
                .uri("/aigateway/-/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.enabled").isEqualTo(true)
                .jsonPath("$.entryUrl").isEqualTo("http://localhost/aigateway")
                .jsonPath("$.models[0]").isEqualTo("gpt-test")
                .jsonPath("$.routeGroups[0]").isEqualTo("fast-group")
                .jsonPath("$.defaultRouteGroup").isEqualTo("fast-group")
                .jsonPath("$.recentErrors").isEqualTo(2)
                .jsonPath("$.version").isEqualTo("1.0.0");
    }

    @Test
    void writesPatClaimsIntoCallerKey() {
        when(aiGateway.complete(any())).thenReturn(Mono.just(result()));
        var jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("alice")
                .claim("pat_name", "pat-1")
                .claim("tokenId", "tid-1")
                .build();
        var patClient = clientWithPrincipal(new JwtAuthenticationToken(jwt, List.of(), "alice"));

        patClient.post()
                .uri("/aigateway/-/openai/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-test","messages":[{"role":"user","content":"hi"}]}
                        """)
                .exchange()
                .expectStatus().isOk();

        var captured = ArgumentCaptor.forClass(AiGatewayInvocation.class);
        verify(aiGateway).complete(captured.capture());
        assertThat(captured.getValue().callerKey()).isEqualTo("pat-1");
        assertThat(captured.getValue().modelOverride()).isEqualTo("gpt-test");
    }

    @Test
    void rejectsAnEmptyBody() {

        client.post()
                .uri("/aigateway/-/openai/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void addsPlanUsageHeadersToACompletedResponse() {
        when(aiGateway.complete(any())).thenReturn(Mono.just(result()));
        when(planUsage.usage("user-1")).thenReturn(Mono.just(usage()));

        client.post()
                .uri("/aigateway/-/openai/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-test","messages":[{"role":"user","content":"hi"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Plan-Name", "标准套餐")
                .expectHeader().valueEquals("X-Plan-5h-Limit", "3000000")
                .expectHeader().valueEquals("X-Plan-5h-Remaining", "2999900")
                .expectHeader().valueEquals("X-Plan-5h-Reset", "2026-11-19T09:00:00Z")
                .expectHeader().valueEquals("X-Plan-Weekly-Limit", "20000000")
                .expectHeader().valueEquals("X-Plan-Weekly-Remaining", "19999900");
    }

    @Test
    void skipsPlanUsageHeadersWithoutAPlan() {
        when(aiGateway.complete(any())).thenReturn(Mono.just(result()));

        client.post()
                .uri("/aigateway/-/openai/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-test","messages":[{"role":"user","content":"hi"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().doesNotExist("X-Plan-Name");
    }

    @Test
    void mapsPlanQuotaExhaustionToOpenAiInsufficientQuota() {
        when(aiGateway.complete(any())).thenReturn(Mono.error(new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                PlanQuotas.CODE_QUOTA_EXCEEDED,
                "5 小时窗口额度不足。")));

        client.post()
                .uri("/aigateway/-/openai/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"model":"gpt-test","messages":[{"role":"user","content":"hi"}]}
                        """)
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                .expectBody()
                .jsonPath("$.error.type").isEqualTo("insufficient_quota")
                .jsonPath("$.error.code").isEqualTo(PlanQuotas.CODE_QUOTA_EXCEEDED);
    }

    private static UsageView usage() {
        return new UsageView(
                new PlanSummary("standard", "标准套餐", "DefaultPlan"),
                new WindowUsage(
                        3_000_000,
                        100,
                        0,
                        2_999_900,
                        Instant.parse("2026-11-19T04:00:00Z"),
                        Instant.parse("2026-11-19T09:00:00Z"),
                        "ACTIVE"),
                new WindowUsage(
                        20_000_000,
                        100,
                        0,
                        19_999_900,
                        Instant.parse("2026-11-16T16:00:00Z"),
                        Instant.parse("2026-11-23T16:00:00Z"),
                        "ACTIVE"),
                new PlanUsage.RateLimit(4, 120),
                null);
    }

    private WebTestClient clientWithPrincipal(Principal principal) {
        var mapper = new ObjectMapper();
        var endpoint = new AiGatewayEndpoint(
                new AiProtocolCodecRegistry(mapper),
                aiGateway,
                new AiResponseEncoders(mapper, new GeminiGatewayCodec(mapper)),
                new GeminiGatewayCodec(mapper),
                gatewayHealth,
                requests,
                planUsage);
        return WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(principal)).build()))
                .build();
    }

    private static Principal principal() {
        return () -> "alice";
    }


    private static AiGatewayResult result() {
        return new AiGatewayResult(
                new AiChatResponse(
                        "resp-1",
                        "gpt-test",
                        new AiChatMessage("assistant", List.of(new AiContentPart.Text("pong", null))),
                        new AiTokenUsage(1, 2, null, null),
                        "stop"),
                meta(),
                new AiTokenUsage(1, 2, null, null),
                BigDecimal.ONE,
                "PROVIDER");
    }

    private static AiGatewayMeta meta() {
        return new AiGatewayMeta(
                "req-1", 7L, "fast", "fast-group", "provider-a", "account-a", "gpt-test",
                AiModel.Tier.CHEAP, "default", "default", "session-1");
    }
}
