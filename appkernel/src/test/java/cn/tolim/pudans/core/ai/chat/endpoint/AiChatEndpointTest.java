package cn.tolim.pudans.core.ai.chat.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.chat.AiChatCompletionResult;
import cn.tolim.pudans.core.ai.chat.AiChatService;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.chat.AiChatStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.rag.Citation;
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
class AiChatEndpointTest {

    @Mock
    AiChatService chatService;

    @Mock
    AiEndpointRequests requests;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        when(requests.userId(any(), anyString(), anyString())).thenReturn(Mono.just("user-1"));
        var endpoint = new AiChatEndpoint(chatService, requests, new ObjectMapper());
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(principal())).build()))
                .build();
    }

    @Test
    void completesAChatRequest() {
        when(chatService.complete(any())).thenReturn(Mono.just(result()));

        client.post()
                .uri("/chats/-/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"mode":"fast","messages":[{"role":"user","content":"你好"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.content").isEqualTo("你好，有什么可以帮你？")
                .jsonPath("$.citations[0].index").isEqualTo(1);
    }

    @Test
    void streamsChatEvents() {
        when(chatService.stream(any())).thenReturn(Flux.just(
                new AiChatStreamEvent.Meta(new AiChatCompletionResult.StreamMeta(
                        "req-1", 7L, "fast", "fast-group", "provider-a", "account-a", "gpt-test",
                        "CHEAP", "default", "default", "session-1")),
                new AiChatStreamEvent.CitationEvent(new Citation(
                        1, 11L, "manual.md", null, "1. 引言", 0.9, "片段")),
                new AiChatStreamEvent.Delta("你好", null),
                new AiChatStreamEvent.Usage(new AiTokenUsage(1, 2, null, null), BigDecimal.ONE, "PROVIDER"),
                new AiChatStreamEvent.Done("21", "stop", false)));

        client.post()
                .uri("/chats/-/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"mode":"fast","messages":[{"role":"user","content":"你好"}]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: meta");
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: citation");
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: delta");
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: done");
                });
    }

    @Test
    void rejectsAnEmptyBody() {

        client.post()
                .uri("/chats/-/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static Principal principal() {
        return () -> "alice";
    }


    private static AiChatCompletionResult result() {
        return new AiChatCompletionResult(
                7L,
                "21",
                "你好，有什么可以帮你？",
                "gpt-test",
                "provider-a",
                "account-a",
                new AiTokenUsage(1, 2, null, null),
                BigDecimal.ONE,
                List.of(new Citation(1, 11L, "manual.md", null, "1. 引言", 0.9, "片段")));
    }
}
