package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolCodecRegistry;
import cn.tolim.pudans.core.ai.gateway.protocol.AiResponseEncoders;
import cn.tolim.pudans.core.ai.gateway.protocol.GeminiGatewayCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiGatewayEndpointTest {

    @Mock
    AiGatewayService gatewayService;

    @Mock
    AiEndpointRequests requests;

    @Mock
    AiGatewayStatusService statusService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        lenient().when(requests.userId(any(), anyString(), anyString())).thenReturn(Mono.just("user-1"));
        var endpoint = new AiGatewayEndpoint(
                new AiProtocolCodecRegistry(mapper),
                gatewayService,
                new AiResponseEncoders(mapper, new GeminiGatewayCodec(mapper)),
                new GeminiGatewayCodec(mapper),
                statusService,
                requests);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(principal())).build()))
                .build();
    }

    @Test
    void completesAnOpenAiChatRequest() {
        when(gatewayService.complete(any())).thenReturn(Mono.just(result()));

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
        when(gatewayService.stream(any())).thenReturn(Flux.just(
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
        when(gatewayService.complete(any())).thenReturn(Mono.just(result()));

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
        when(gatewayService.stream(any())).thenReturn(Flux.just(
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
        when(statusService.status()).thenReturn(Mono.just(new AiGatewayStatusService.Status(
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
        when(gatewayService.complete(any())).thenReturn(Mono.just(result()));
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
        verify(gatewayService).complete(captured.capture());
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

    private WebTestClient clientWithPrincipal(Principal principal) {
        var mapper = new ObjectMapper();
        var endpoint = new AiGatewayEndpoint(
                new AiProtocolCodecRegistry(mapper),
                gatewayService,
                new AiResponseEncoders(mapper, new GeminiGatewayCodec(mapper)),
                new GeminiGatewayCodec(mapper),
                statusService,
                requests);
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
