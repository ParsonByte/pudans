package cn.tolim.pudans.core.ai.chat.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.core.ai.chat.AiChatCompletionResult;
import cn.tolim.pudans.core.ai.chat.ChatCompletions;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.chat.AiChatStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.rag.Citation;
import cn.tolim.pudans.core.storage.StorageTransfer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiChatEndpointTest {

    @Mock
    ChatCompletions chatCompletions;

    @Mock
    AiEndpointRequests requests;

    @Mock
    StorageTransfer storageTransfer;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        when(requests.userId(any(), anyString(), anyString())).thenReturn(Mono.just("user-1"));
        var endpoint = new AiChatEndpoint(chatCompletions, requests, storageTransfer, new ObjectMapper());
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(principal())).build()))
                .build();
    }

    @Test
    void completesAChatRequest() {
        when(chatCompletions.complete(any())).thenReturn(Mono.just(result()));

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
                .jsonPath("$.units").isEqualTo(1)
                .jsonPath("$.points").doesNotExist()
                .jsonPath("$.citations[0].index").isEqualTo(1);
    }

    @Test
    void streamsChatEvents() {
        when(chatCompletions.stream(any())).thenReturn(Flux.just(
                new AiChatStreamEvent.Meta(new AiChatCompletionResult.StreamMeta(
                        "req-1", 7L, "fast", "fast-group", "provider-a", "account-a", "gpt-test",
                        "CHEAP", "default", "default", "session-1")),
                new AiChatStreamEvent.CitationEvent(new Citation(
                        1, 11L, "manual.md", null, "1. 引言", 0.9, "片段")),
                new AiChatStreamEvent.ToolConfirm("run-1", "call-1", "echo", "需要确认。"),
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
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: tool_confirm");
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: delta");
                    org.assertj.core.api.Assertions.assertThat(body).contains("\"units\":1");
                    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("\"points\"");
                    org.assertj.core.api.Assertions.assertThat(body).contains("event: done");
                });
    }

    @Test
    void confirmsAPendingToolCall() {
        when(chatCompletions.confirm("user-1", "run-1", "call-1", true)).thenReturn(true);

        client.post()
                .uri("/chats/-/tool-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"runId":"run-1","toolCallId":"call-1","approved":true}
                        """)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void submitsAnExternalToolResult() {
        when(chatCompletions.submitExternalResult("user-1", "run-1", "call-1", "外部结果", false))
                .thenReturn(true);

        client.post()
                .uri("/chats/-/tool-external-results")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"runId":"run-1","toolCallId":"call-1","output":"外部结果","error":false}
                        """)
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void interruptsARun() {
        when(chatCompletions.interrupt("user-1", "run-1")).thenReturn(true);

        client.post()
                .uri("/chats/-/runs/run-1/interrupt")
                .exchange()
                .expectStatus().isAccepted();
    }

    @Test
    void reportsAnUnknownToolConfirmation() {
        when(chatCompletions.confirm("user-1", "run-1", "call-1", false)).thenReturn(false);

        client.post()
                .uri("/chats/-/tool-confirmations")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"runId":"run-1","toolCallId":"call-1","approved":false}
                        """)
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void rejectsAnEmptyBody() {

        client.post()
                .uri("/chats/-/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void uploadsAnImageAttachment() {
        when(storageTransfer.upload(any())).thenReturn(Mono.just(storageFile()));
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(new byte[] {1, 2, 3}))
                .filename("image.png")
                .contentType(MediaType.IMAGE_PNG);

        client.post()
                .uri("/chats/-/attachments")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fileId").isEqualTo("file-1")
                .jsonPath("$.name").isEqualTo("image.png")
                .jsonPath("$.contentType").isEqualTo("image/png")
                .jsonPath("$.size").isEqualTo(3)
                .jsonPath("$.type").isEqualTo("IMAGE");
    }

    @Test
    void uploadsADocumentAttachment() {
        when(storageTransfer.upload(any())).thenReturn(Mono.just(storageFile("notes.pdf", "application/pdf")));
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource(new byte[] {1, 2, 3}))
                .filename("notes.pdf")
                .contentType(MediaType.APPLICATION_PDF);

        client.post()
                .uri("/chats/-/attachments")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("notes.pdf")
                .jsonPath("$.contentType").isEqualTo("application/pdf")
                .jsonPath("$.type").isEqualTo("DOCUMENT");
    }

    @Test
    void rejectsAnUnsafeAttachment() {
        var builder = new MultipartBodyBuilder();
        builder.part("file", new ByteArrayResource("<html></html>".getBytes()))
                .filename("page.html")
                .contentType(MediaType.TEXT_HTML);

        client.post()
                .uri("/chats/-/attachments")
                .body(BodyInserters.fromMultipartData(builder.build()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static StorageFile storageFile() {
        return storageFile("image.png", "image/png");
    }

    private static StorageFile storageFile(String name, String contentType) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        file.getSpec().setOriginalName(name);
        file.getSpec().setContentType(contentType);
        file.getSpec().setSize(3);
        file.getSpec().setState(StorageFileState.ACTIVE);
        return file;
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
