package cn.tolim.pudans.core.ai.protocol.a2a;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.harness.agent.AgentResult;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import cn.tolim.pudans.core.ai.protocol.ProtocolAgentRuntime;
import cn.tolim.pudans.core.ai.team.Teams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
class A2aEndpointTest {

    private static final String USER = "user-1";

    @Mock
    ProtocolAgentRuntime runtime;

    @Mock
    AiEndpointRequests requests;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private A2aTaskStore store;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        lenient().when(requests.userId(any())).thenReturn(Mono.just(USER));
        store = new A2aTaskStore();
        var handler = new A2aRequestHandler(runtime, store);
        client = WebTestClient.bindToRouterFunction(new A2aEndpoint(handler, requests, mapper).endpoint())
                .build();
    }

    @Test
    void returnsTheDefaultAgentCard() {
        var card = get("/aia2a/.well-known/agent.json");

        assertThat(card.path("name").asText()).isEqualTo(Teams.DEFAULT_AGENT);
        assertThat(card.path("url").asText()).endsWith("/aia2a");
        assertThat(card.path("capabilities").path("streaming").asBoolean()).isTrue();
        assertThat(card.path("skills").isArray()).isTrue();
        assertThat(card.path("skills").size()).isZero();
    }

    @Test
    void returnsTheRequestedAgentCard() {
        var card = get("/aia2a/.well-known/agent.json?agent=agent-a");

        assertThat(card.path("name").asText()).isEqualTo("agent-a");
    }

    @Test
    void rejectsMalformedJson() {
        var response = rpc("{");

        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.path("id").isNull()).isTrue();
        assertThat(response.path("error").path("code").asInt())
                .isEqualTo(A2aRpcException.PARSE_ERROR);
    }

    @Test
    void rejectsAnUnknownMethod() {
        var response = rpc("""
                {"jsonrpc":"2.0","id":3,"method":"unknown/method","params":{}}
                """);

        assertThat(response.path("id").asInt()).isEqualTo(3);
        assertThat(response.path("error").path("code").asInt())
                .isEqualTo(A2aRpcException.METHOD_NOT_FOUND);
    }

    @Test
    void rejectsAMessageWithoutTextParts() {
        var response = rpc("""
                {"jsonrpc":"2.0","id":4,"method":"message/send","params":{
                  "message":{"role":"user","messageId":"m-1",
                             "parts":[{"kind":"file","file":{"uri":"file:///tmp/a.txt"}}]}}}
                """);

        assertThat(response.path("error").path("code").asInt())
                .isEqualTo(A2aRpcException.INVALID_PARAMS);
        verifyNoInteractions(runtime);
    }

    @Test
    void sendsAMessageAndReturnsTheCompletedTask() {
        var captor = ArgumentCaptor.forClass(ProtocolAgentRuntime.ProtocolRunRequest.class);
        when(runtime.start(captor.capture())).thenReturn(Mono.just(successfulRun()));

        var response = rpc("""
                {"jsonrpc":"2.0","id":"1","method":"message/send","params":{
                  "message":{"role":"user","messageId":"m-1","contextId":"ctx-1",
                             "parts":[{"kind":"text","text":"你好"}]},
                  "metadata":{"agentId":"agent-a"}}}
                """);

        var result = response.path("result");
        var taskId = result.path("id").asText();
        assertThat(response.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(response.path("id").asText()).isEqualTo("1");
        assertThat(result.path("kind").asText()).isEqualTo(A2aTask.KIND_TASK);
        assertThat(UUID.fromString(taskId).toString()).isEqualTo(taskId);
        assertThat(result.path("contextId").asText()).isEqualTo("ctx-1");
        assertThat(result.path("status").path("state").asText()).isEqualTo(A2aTaskStatus.COMPLETED);
        assertThat(result.path("artifacts").size()).isEqualTo(1);
        assertThat(result.path("artifacts").path(0).path("parts").path(0).path("kind").asText())
                .isEqualTo("text");
        assertThat(result.path("artifacts").path(0).path("parts").path(0).path("text").asText())
                .isEqualTo("回答");

        var request = captor.getValue();
        assertThat(request.userId()).isEqualTo(USER);
        assertThat(request.agentName()).isEqualTo("agent-a");
        assertThat(request.threadId()).isEqualTo("ctx-1");
        assertThat(request.streaming()).isFalse();
        assertThat(request.input().singleText()).isEqualTo("你好");
    }

    @Test
    void returnsAnInternalErrorWhenTheRunFails() {
        var run = new AgentRun("run-fail");
        run.attach(Mono.error(new IllegalStateException("模型调用失败。")));
        when(runtime.start(any())).thenReturn(Mono.just(run));

        var response = rpc("""
                {"jsonrpc":"2.0","id":2,"method":"message/send","params":{
                  "message":{"role":"user","messageId":"m-1","taskId":"task-fail",
                             "parts":[{"kind":"text","text":"你好"}]}}}
                """);

        assertThat(response.path("error").path("code").asInt())
                .isEqualTo(A2aRpcException.INTERNAL_ERROR);
        assertThat(response.path("error").path("message").asText()).contains("模型调用失败。");
        assertThat(store.find(USER, "task-fail").block().status().state())
                .isEqualTo(A2aTaskStatus.FAILED);
    }

    @Test
    void returnsAnInternalErrorWhenTheRuntimeFailsToStart() {
        when(runtime.start(any())).thenReturn(Mono.error(new IllegalStateException("智能体不存在。")));

        var response = rpc("""
                {"jsonrpc":"2.0","id":2,"method":"message/send","params":{
                  "message":{"role":"user","messageId":"m-1","taskId":"task-start-fail",
                             "parts":[{"kind":"text","text":"你好"}]}}}
                """);

        assertThat(response.path("error").path("code").asInt())
                .isEqualTo(A2aRpcException.INTERNAL_ERROR);
        assertThat(store.find(USER, "task-start-fail").block()).isNull();
    }

    @Test
    void getsAStoredTask() {
        when(runtime.start(any())).thenReturn(Mono.just(successfulRun()));
        var send = rpc("""
                {"jsonrpc":"2.0","id":5,"method":"message/send","params":{
                  "message":{"role":"user","messageId":"m-1","contextId":"ctx-1",
                             "parts":[{"kind":"text","text":"你好"}]}}}
                """);
        var taskId = send.path("result").path("id").asText();

        var response = rpc("""
                {"jsonrpc":"2.0","id":6,"method":"tasks/get","params":{"id":"%s"}}
                """.formatted(taskId));

        var result = response.path("result");
        assertThat(result.path("id").asText()).isEqualTo(taskId);
        assertThat(result.path("contextId").asText()).isEqualTo("ctx-1");
        assertThat(result.path("status").path("state").asText()).isEqualTo(A2aTaskStatus.COMPLETED);
        assertThat(result.path("artifacts").path(0).path("parts").path(0).path("text").asText())
                .isEqualTo("回答");
    }

    @Test
    void returnsTaskNotFoundForAnUnknownTask() {
        var response = rpc("""
                {"jsonrpc":"2.0","id":7,"method":"tasks/get","params":{"id":"missing"}}
                """);

        assertThat(response.path("error").path("code").asInt())
                .isEqualTo(A2aRpcException.TASK_NOT_FOUND);
    }

    @Test
    void cancelsARunningTask() {
        var run = new AgentRun("run-cancel");
        run.attach(Mono.never());
        var record = new A2aTaskRecord(USER, "task-cancel", "ctx-1");
        record.attach(run);
        store.save(record);

        var response = rpc("""
                {"jsonrpc":"2.0","id":8,"method":"tasks/cancel","params":{"id":"task-cancel"}}
                """);

        assertThat(response.path("result").path("status").path("state").asText())
                .isEqualTo(A2aTaskStatus.CANCELED);
        assertThat(run.cancelled()).isTrue();
        assertThat(record.status().state()).isEqualTo(A2aTaskStatus.CANCELED);
    }

    @Test
    void rejectsCancellingACompletedTask() {
        when(runtime.start(any())).thenReturn(Mono.just(successfulRun()));
        rpc("""
                {"jsonrpc":"2.0","id":9,"method":"message/send","params":{
                  "message":{"role":"user","messageId":"m-1","taskId":"task-done",
                             "parts":[{"kind":"text","text":"你好"}]}}}
                """);

        var response = rpc("""
                {"jsonrpc":"2.0","id":10,"method":"tasks/cancel","params":{"id":"task-done"}}
                """);

        assertThat(response.path("error").path("code").asInt())
                .isEqualTo(A2aRpcException.TASK_NOT_CANCELABLE);
    }

    @Test
    void streamsTaskStatusAndArtifactEvents() {
        when(runtime.start(any())).thenReturn(Mono.just(successfulRun()));

        var events = sseEvents("""
                {"jsonrpc":"2.0","id":11,"method":"message/stream","params":{
                  "message":{"role":"user","messageId":"m-1","contextId":"ctx-1",
                             "parts":[{"kind":"text","text":"你好"}]}}}
                """);

        assertThat(events).hasSize(4);
        assertThat(events.get(0).path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(events.get(0).path("id").asInt()).isEqualTo(11);
        assertThat(events.get(0).path("result").path("kind").asText()).isEqualTo("status-update");
        assertThat(events.get(0).path("result").path("status").path("state").asText())
                .isEqualTo(A2aTaskStatus.SUBMITTED);
        assertThat(events.get(0).path("result").path("final").asBoolean()).isFalse();
        assertThat(events.get(1).path("result").path("status").path("state").asText())
                .isEqualTo(A2aTaskStatus.WORKING);
        assertThat(events.get(2).path("result").path("kind").asText()).isEqualTo("artifact-update");
        assertThat(events.get(2).path("result").path("artifact").path("parts").path(0).path("text").asText())
                .isEqualTo("回答");
        assertThat(events.get(2).path("result").path("lastChunk").asBoolean()).isTrue();
        assertThat(events.get(3).path("result").path("status").path("state").asText())
                .isEqualTo(A2aTaskStatus.COMPLETED);
        assertThat(events.get(3).path("result").path("final").asBoolean()).isTrue();
        assertThat(events.get(2).path("result").path("taskId").asText())
                .isEqualTo(events.get(3).path("result").path("taskId").asText());
    }

    @Test
    void streamsAFailedStatusWhenTheRunFails() {
        var run = new AgentRun("run-stream-fail");
        run.attach(Mono.error(new IllegalStateException("模型调用失败。")));
        when(runtime.start(any())).thenReturn(Mono.just(run));

        var events = sseEvents("""
                {"jsonrpc":"2.0","id":12,"method":"message/stream","params":{
                  "message":{"role":"user","messageId":"m-1","taskId":"task-stream-fail",
                             "parts":[{"kind":"text","text":"你好"}]}}}
                """);

        assertThat(events).hasSize(3);
        assertThat(events.get(2).path("result").path("status").path("state").asText())
                .isEqualTo(A2aTaskStatus.FAILED);
        assertThat(events.get(2).path("result").path("final").asBoolean()).isTrue();
        assertThat(store.find(USER, "task-stream-fail").block().status().state())
                .isEqualTo(A2aTaskStatus.FAILED);
    }

    private JsonNode get(String uri) {
        var body = client.get()
                .uri(uri)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();
        return read(Objects.requireNonNull(body, "响应体不能为空"));
    }

    private JsonNode rpc(String body) {
        return read(rpcText(body));
    }

    private String rpcText(String body) {
        var response = client.post()
                .uri("/aia2a")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(String.class)
                .getResponseBody()
                .blockFirst();
        return Objects.requireNonNull(response, "响应体不能为空");
    }

    private List<JsonNode> sseEvents(String body) {
        var frames = client.post()
                .uri("/aia2a")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus()
                .isOk()
                .returnResult(String.class)
                .getResponseBody()
                .collectList()
                .block();
        return Objects.requireNonNull(frames, "事件流不能为空").stream()
                .map(this::read)
                .toList();
    }

    private JsonNode read(String body) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("响应不是有效 JSON。", error);
        }
    }

    private static AgentRun successfulRun() {
        var run = new AgentRun("run-1");
        run.attach(Mono.fromSupplier(() -> {
            run.emit(new AgentEvent.AgentStart("run-1", "agent-a"));
            run.emit(new AgentEvent.AgentResult("run-1", "回答"));
            return new AgentResult("回答", null);
        }));
        return run;
    }
}