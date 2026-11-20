package cn.tolim.pudans.core.ai.protocol.agui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.harness.agent.AgentResult;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import cn.tolim.pudans.core.ai.protocol.ProtocolAgentRuntime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AguiEndpointTest {

    private static final String USER = "user-1";

    private static final String RUN_INPUT = """
            {"threadId":"thread-1","runId":"run-1","messages":[
              {"id":"m1","role":"user","content":"你好"}]}
            """;

    @Mock
    ProtocolAgentRuntime runtime;

    @Mock
    AiEndpointRequests requests;

    private final ObjectMapper mapper = new ObjectMapper();

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(requests.userId(any())).thenReturn(Mono.just(USER));
        var endpoint = new AguiEndpoint(runtime, requests, mapper);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void streamsAguiEventsForARun() {
        when(runtime.start(any())).thenReturn(Mono.just(successfulRun()));

        client.post()
                .uri("/aiagui/-/run")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(RUN_INPUT)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> assertThat(types(body)).containsExactly(
                        "RUN_STARTED",
                        "TEXT_MESSAGE_START",
                        "TEXT_MESSAGE_CONTENT",
                        "TEXT_MESSAGE_END",
                        "TOOL_CALL_START",
                        "TOOL_CALL_ARGS",
                        "TOOL_CALL_END",
                        "TOOL_CALL_RESULT",
                        "RUN_FINISHED"));

        var captured = ArgumentCaptor.forClass(ProtocolAgentRuntime.ProtocolRunRequest.class);
        verify(runtime).start(captured.capture());
        var request = captured.getValue();
        assertThat(request.userId()).isEqualTo(USER);
        assertThat(request.threadId()).isEqualTo("thread-1");
        assertThat(request.agentName()).isNull();
        assertThat(request.streaming()).isTrue();
        assertThat(request.input().singleText()).isEqualTo("你好");
    }

    @Test
    void usesHeaderAgentAndGeneratesMissingThreadId() {
        when(runtime.start(any())).thenReturn(Mono.just(successfulRun()));

        client.post()
                .uri("/aiagui/-/run")
                .header("X-Agent", "agent-b")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"messages":[{"id":"m1","role":"user","content":"你好"}]}
                        """)
                .exchange()
                .expectStatus().isOk();

        var captured = ArgumentCaptor.forClass(ProtocolAgentRuntime.ProtocolRunRequest.class);
        verify(runtime).start(captured.capture());
        assertThat(captured.getValue().agentName()).isEqualTo("agent-b");
        assertThat(captured.getValue().threadId()).startsWith("thread-");
    }

    @Test
    void streamsRunErrorWhenTheRunFails() {
        var run = new AgentRun("run-2");
        run.attach(Mono.defer(() -> {
            run.emit(new AgentEvent.AgentStart("run-2", "agent-a"));
            run.emit(new AgentEvent.TextBlockDelta("run-2", "部分"));
            return Mono.error(new IllegalStateException("模型调用失败。"));
        }));
        when(runtime.start(any())).thenReturn(Mono.just(run));

        client.post()
                .uri("/aiagui/-/run")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(RUN_INPUT)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(types(body)).containsExactly(
                            "RUN_STARTED",
                            "TEXT_MESSAGE_START",
                            "TEXT_MESSAGE_CONTENT",
                            "TEXT_MESSAGE_END",
                            "RUN_ERROR");
                    assertThat(body).contains("模型调用失败。").doesNotContain("RUN_FINISHED");
                });
    }

    @Test
    void rejectsARequestWithoutUserMessages() {
        client.post()
                .uri("/aiagui/-/run")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"threadId":"thread-1","messages":[{"id":"m1","role":"assistant","content":"回答"}]}
                        """)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(runtime);
    }

    @Test
    void rejectsAnEmptyBody() {
        client.post()
                .uri("/aiagui/-/run")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest();

        verifyNoInteractions(runtime);
    }

    private List<String> types(String body) {
        return Arrays.stream(body.split("\n\n"))
                .map(String::trim)
                .filter(part -> part.startsWith("data: "))
                .map(part -> part.substring("data: ".length()))
                .map(this::typeOf)
                .toList();
    }

    private String typeOf(String json) {
        try {
            return mapper.readTree(json).path("type").asText();
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("AG-UI 事件不是有效 JSON。", error);
        }
    }

    private static AgentRun successfulRun() {
        var run = new AgentRun("run-1");
        run.attach(Mono.fromSupplier(() -> {
            run.emit(new AgentEvent.AgentStart("run-1", "agent-a"));
            run.emit(new AgentEvent.TextBlockStart("run-1"));
            run.emit(new AgentEvent.TextBlockDelta("run-1", "你好"));
            run.emit(new AgentEvent.TextBlockEnd("run-1", "你好"));
            run.emit(new AgentEvent.ToolCallStart("run-1", "call-1", "search", "{\"q\":\"pudans\"}"));
            run.emit(new AgentEvent.ToolCallEnd("run-1", "call-1", "search", "{\"q\":\"pudans\"}"));
            run.emit(new AgentEvent.ToolResultEnd("run-1", "call-1", "search", "结果", false));
            var result = new AgentResult("你好", null);
            run.emit(new AgentEvent.AgentResult("run-1", "你好"));
            return result;
        }));
        return run;
    }
}