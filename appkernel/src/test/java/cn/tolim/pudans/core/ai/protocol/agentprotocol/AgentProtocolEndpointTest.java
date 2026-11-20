package cn.tolim.pudans.core.ai.protocol.agentprotocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.harness.agent.AgentResult;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import cn.tolim.pudans.core.ai.protocol.ProtocolAgentRuntime;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.Principal;
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
class AgentProtocolEndpointTest {

    @Mock
    ProtocolAgentRuntime runtime;

    @Mock
    AiEndpointRequests requests;

    private AgentProtocolRunStore store;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        store = new AgentProtocolRunStore();
        when(requests.userId(any())).thenReturn(Mono.just("user-1"));
        var endpoint = new AgentProtocolEndpoint(
                runtime, store, requests, new AgentProtocolEventMapper(new ObjectMapper()));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(principal())).build()))
                .build();
    }

    @Test
    void createsARunWithTheRequestedThread() {
        when(runtime.start(any())).thenReturn(Mono.just(pendingRun()));

        client.post()
                .uri("/aiagentprotocol/-/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"agent_id":"agent-a","thread_id":"thread-1","input":"你好"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.run_id").isNotEmpty()
                .jsonPath("$.thread_id").isEqualTo("thread-1")
                .jsonPath("$.status").isEqualTo("running");
    }

    @Test
    void acceptsMessageListsAndUsesTheLastUserMessage() {
        var captor = ArgumentCaptor.forClass(ProtocolAgentRuntime.ProtocolRunRequest.class);
        when(runtime.start(captor.capture())).thenReturn(Mono.just(pendingRun()));

        client.post()
                .uri("/aiagentprotocol/-/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"agent_id":"agent-a","thread_id":"thread-2","input":{"messages":[
                          {"role":"user","content":"第一问"},
                          {"role":"assistant","content":"回答"},
                          {"role":"user","content":"第二问"}
                        ]}}
                        """)
                .exchange()
                .expectStatus().isOk();

        var request = captor.getValue();
        assertThat(request.input().singleText()).isEqualTo("第二问");
        assertThat(request.threadId()).isEqualTo("thread-2");
        assertThat(request.agentName()).isEqualTo("agent-a");
        assertThat(request.streaming()).isFalse();
    }

    @Test
    void queriesARunById() {
        when(runtime.start(any())).thenReturn(Mono.just(pendingRun()));
        createRun("thread-1");

        var runId = store.latestByThread("user-1", "thread-1").block().runId();
        client.get()
                .uri("/aiagentprotocol/-/runs/" + runId)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.run_id").isEqualTo(runId)
                .jsonPath("$.thread_id").isEqualTo("thread-1")
                .jsonPath("$.agent_id").isEqualTo("agent-a")
                .jsonPath("$.status").isEqualTo("running");
    }

    @Test
    void cancelsARun() {
        var run = pendingRun();
        when(runtime.start(any())).thenReturn(Mono.just(run));
        createRun("thread-1");

        var runId = store.latestByThread("user-1", "thread-1").block().runId();
        client.post()
                .uri("/aiagentprotocol/-/runs/" + runId + "/cancel")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("cancelled");

        assertThat(run.cancelled()).isTrue();
        assertThat(store.find("user-1", runId).block().status())
                .isEqualTo(AgentProtocolRunStatus.CANCELLED);
    }

    @Test
    void returnsTheLatestRunStateForAThread() {
        when(runtime.start(any())).thenReturn(Mono.just(pendingRun()));
        createRun("thread-1");

        var snapshot = store.latestByThread("user-1", "thread-1").block();
        client.get()
                .uri("/aiagentprotocol/-/threads/thread-1/state")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.run_id").isEqualTo(snapshot.runId())
                .jsonPath("$.thread_id").isEqualTo("thread-1")
                .jsonPath("$.status").isEqualTo("running");
    }

    @Test
    void streamsMappedAgentEvents() {
        var run = new AgentRun("run-1");
        run.attach(Mono.fromRunnable(() -> {
                    run.emit(new AgentEvent.TextBlockDelta("run-1", "你好"));
                    run.emit(new AgentEvent.ToolCallStart("run-1", "call-1", "search", "{\"q\":\"pudans\"}"));
                    run.emit(new AgentEvent.ToolResultEnd("run-1", "call-1", "search", "命中", false));
                })
                .thenReturn(new AgentResult("最终回答", null)));
        when(runtime.start(any())).thenReturn(Mono.just(run));

        client.post()
                .uri("/aiagentprotocol/-/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"thread_id":"thread-1","input":"你好","stream":true}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("event: metadata");
                    assertThat(body).contains("\"thread_id\":\"thread-1\"");
                    assertThat(body).contains("event: updates");
                    assertThat(body).contains("\"type\":\"text\"");
                    assertThat(body).contains("\"content\":\"你好\"");
                    assertThat(body).contains("\"type\":\"tool_call\"");
                    assertThat(body).contains("\"name\":\"search\"");
                    assertThat(body).contains("\"type\":\"tool_result\"");
                    assertThat(body).contains("\"output\":\"命中\"");
                    assertThat(body).contains("event: end");
                    assertThat(body).contains("\"status\":\"completed\"");
                    assertThat(body).contains("\"content\":\"最终回答\"");
                    assertThat(body.indexOf("event: metadata")).isLessThan(body.indexOf("event: updates"));
                    assertThat(body.indexOf("event: updates")).isLessThan(body.indexOf("event: end"));
                });

        assertThat(store.latestByThread("user-1", "thread-1").block().status())
                .isEqualTo(AgentProtocolRunStatus.COMPLETED);
    }

    @Test
    void streamsAnErrorAndMarksTheRunFailed() {
        var run = new AgentRun("run-1");
        run.attach(Mono.error(new IllegalStateException("模型失败")));
        when(runtime.start(any())).thenReturn(Mono.just(run));

        client.post()
                .uri("/aiagentprotocol/-/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"thread_id":"thread-1","input":"你好","stream":true}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertThat(body).contains("event: error");
                    assertThat(body).contains("\"status\":\"failed\"");
                    assertThat(body).contains("\"error\":\"模型失败\"");
                });

        var snapshot = store.latestByThread("user-1", "thread-1").block();
        assertThat(snapshot.status()).isEqualTo(AgentProtocolRunStatus.FAILED);
        assertThat(snapshot.error()).isEqualTo("模型失败");
    }

    @Test
    void rejectsAnEmptyInput() {
        client.post()
                .uri("/aiagentprotocol/-/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"thread_id":"thread-1"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void returnsNotFoundForUnknownRuns() {
        client.get()
                .uri("/aiagentprotocol/-/runs/missing")
                .exchange()
                .expectStatus().isNotFound();
    }

    private void createRun(String threadId) {
        client.post()
                .uri("/aiagentprotocol/-/runs")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"agent_id":"agent-a","thread_id":"%s","input":"你好"}
                        """.formatted(threadId))
                .exchange()
                .expectStatus().isOk();
    }

    private static AgentRun pendingRun() {
        var run = new AgentRun("run-1");
        run.attach(Mono.never());
        return run;
    }

    private static Principal principal() {
        return () -> "alice";
    }
}