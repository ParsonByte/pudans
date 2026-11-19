package cn.tolim.pudans.core.ai.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolCodecRegistry;
import cn.tolim.pudans.core.ai.gateway.protocol.AiResponseEncoders;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiGatewayEndpointTest {

    @Mock
    AiGatewayService gatewayService;

    @Mock
    AiEndpointRequests requests;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var mapper = new ObjectMapper();
        when(requests.userId(any(), anyString(), anyString())).thenReturn(Mono.just("user-1"));
        var endpoint = new AiGatewayEndpoint(
                new AiProtocolCodecRegistry(mapper), gatewayService, new AiResponseEncoders(mapper), requests);
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
    void rejectsAnEmptyBody() {

        client.post()
                .uri("/aigateway/-/openai/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
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
