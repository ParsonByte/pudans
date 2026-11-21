package cn.tolim.pudans.core.ai.harness.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import cn.tolim.pudans.core.ai.harness.compaction.CompactionConfig;
import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import cn.tolim.pudans.core.ai.harness.event.AgentEventType;
import cn.tolim.pudans.core.ai.harness.journal.InMemoryThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.journal.JournalEvent;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventType;
import cn.tolim.pudans.core.ai.harness.permission.PermissionContext;
import cn.tolim.pudans.core.ai.harness.permission.PermissionEngine;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentRuntime;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentTools;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultEvictionConfig;
import cn.tolim.pudans.core.ai.harness.tool.ToolResultStore;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ReActAgentTest {

    @Mock
    ChatModel chatModel;

    @Mock
    VersionedAgentStateStore versionedStateStore;

    @Test
    void executesToolCallsAndReturnsTheFinalAnswer() {
        var toolkit = new HarnessToolkit().register(new EchoTools());
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var stateStore = new InMemoryAgentStateStore();
        var agent = ReActAgent.builder()
                .name("test-agent")
                .systemPrompt("Be helpful.")
                .chatModel(chatModel)
                .toolkit(toolkit)
                .stateStore(stateStore)
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-1")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, org.mockito.Mockito.times(2)).chat(requests.capture());
        assertThat(requests.getAllValues().get(1).messages())
                .anyMatch(message -> message instanceof ToolExecutionResultMessage result
                        && result.text().equals("echo:hello")
                        && !result.isError());
        var state = stateStore.load("user-1", "session-1").block();
        assertThat(state).isNotNull();
        assertThat(state.messages()).hasSize(4);
    }

    @Test
    void returnsWithoutCallingToolsWhenTheModelStops() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response(AiMessage.from("plain answer")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .build();

        StepVerifier.create(agent.call("hello", RuntimeContext.of("user-1", "session-2")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("plain answer"))
                .verifyComplete();

        verify(chatModel).chat(any(ChatRequest.class));
    }

    @Test
    void streamsTextDeltasWhenAStreamingModelIsConfigured() {
        var streaming = new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onPartialResponse("你");
                handler.onPartialResponse("好");
                handler.onCompleteResponse(response(AiMessage.from("你好")));
            }
        };
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .streamingChatModel(streaming)
                .build();
        var run = agent.start(
                UserMessage.from("hello"), RuntimeContext.of("user-1", "session-stream"), true);

        StepVerifier.create(run.result())
                .assertNext(result -> assertThat(result.content()).isEqualTo("你好"))
                .verifyComplete();

        var events = run.events()
                .takeUntil(event -> event.type() == AgentEventType.AGENT_END)
                .collectList()
                .block();
        assertThat(events).extracting(AgentEvent::type).contains(
                AgentEventType.TEXT_BLOCK_START,
                AgentEventType.TEXT_BLOCK_DELTA,
                AgentEventType.TEXT_BLOCK_END);
        assertThat(events.stream()
                        .filter(AgentEvent.TextBlockDelta.class::isInstance)
                        .map(AgentEvent.TextBlockDelta.class::cast)
                        .map(AgentEvent.TextBlockDelta::delta))
                .containsExactly("你", "好");
    }

    @Test
    void emitsCompleteOnlyStreamingTextAsASingleDelta() {
        var streaming = new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                handler.onCompleteResponse(response(AiMessage.from("done")));
            }
        };
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .streamingChatModel(streaming)
                .build();
        var run = agent.start(
                UserMessage.from("hello"), RuntimeContext.of("user-1", "session-stream-complete"), true);

        StepVerifier.create(run.result())
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var deltas = run.events()
                .takeUntil(event -> event.type() == AgentEventType.AGENT_END)
                .filter(AgentEvent.TextBlockDelta.class::isInstance)
                .cast(AgentEvent.TextBlockDelta.class)
                .map(AgentEvent.TextBlockDelta::delta)
                .collectList()
                .block();
        assertThat(deltas).containsExactly("done");
    }

    @Test
    void keepsTheToolLoopWhenStreaming() {
        var calls = new AtomicInteger();
        var streaming = new StreamingChatModel() {

            @Override
            public void doChat(ChatRequest request, StreamingChatResponseHandler handler) {
                if (calls.incrementAndGet() == 1) {
                    handler.onCompleteResponse(response(toolCall("1", "echo", "{\"text\":\"hello\"}")));
                    return;
                }
                handler.onPartialResponse("done");
                handler.onCompleteResponse(response(AiMessage.from("done")));
            }
        };
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .streamingChatModel(streaming)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .build();

        StepVerifier.create(agent.start(
                                UserMessage.from("run"),
                                RuntimeContext.of("user-1", "session-stream-tools"),
                                true)
                        .result())
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        assertThat(calls).hasValue(2);
    }

    @Test
    void journalsRunModelAndStateEvents() {
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response(AiMessage.from("done")));
        var journalStore = new InMemoryThreadJournalStore();
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .journalStore(journalStore)
                .build();
        var context = RuntimeContext.of("user-1", "session-journal");

        StepVerifier.create(agent.call("hello", context))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var events = journalStore
                .open(context)
                .flatMapMany(journal -> journal.scan(0, Long.MAX_VALUE))
                .collectList()
                .block();
        assertThat(events).extracting(JournalEvent::type).contains(
                JournalEventType.RUN_START,
                JournalEventType.MODEL_CALL_START,
                JournalEventType.MODEL_CALL_END,
                JournalEventType.RUN_OUTPUT,
                JournalEventType.STATE_CHECKPOINT,
                JournalEventType.RUN_END);
    }

    @Test
    void cancelsBeforeTheFirstModelCall() {
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .build();
        var run = agent.start("hello", RuntimeContext.of("user-1", "session-3"));

        run.cancel();

        StepVerifier.create(run.result())
                .expectError(AgentInterruptedException.class)
                .verify();
    }

    @Test
    void emitsTypedEventsForTextAndToolExecution() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .build();
        var run = agent.start("run", RuntimeContext.of("user-1", "session-events"));

        assertThat(run.result().block()).isNotNull();

        var events = run.events()
                .takeUntil(event -> event.type() == AgentEventType.AGENT_END)
                .collectList()
                .block();
        assertThat(events).extracting(event -> event.type()).contains(
                AgentEventType.AGENT_START,
                AgentEventType.MODEL_CALL_START,
                AgentEventType.TOOL_CALL_START,
                AgentEventType.TOOL_RESULT_END,
                AgentEventType.TEXT_BLOCK_END,
                AgentEventType.AGENT_RESULT,
                AgentEventType.AGENT_END);
    }

    @Test
    void deniesToolsAccordingToThePermissionEngine() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.builder()
                        .deny("echo")
                        .build()))
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-denied")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, org.mockito.Mockito.times(2)).chat(requests.capture());
        assertThat(requests.getAllValues().get(1).messages())
                .anyMatch(message -> message instanceof ToolExecutionResultMessage result
                        && result.isError()
                        && result.text().contains("拒绝"));
    }

    @Test
    void persistsPlanModeAndTodoState() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "plan_enter", "{}")))
                .thenReturn(response(toolCall("2", "plan_write", "{\"content\":\"Step 1\"}")))
                .thenReturn(response(toolCall(
                        "3",
                        "todo_write",
                        "{\"items\":[{\"id\":\"1\",\"content\":\"Do X\",\"status\":\"pending\"}]}")))
                .thenReturn(response(AiMessage.from("done")));
        var stateStore = new InMemoryAgentStateStore();
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .stateStore(stateStore)
                .planModeEnabled(true)
                .todoEnabled(true)
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-plan")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var state = stateStore.load("user-1", "session-plan").block();
        assertThat(state).isNotNull();
        assertThat(state.planMode().active()).isTrue();
        assertThat(state.planMode().planContent()).isEqualTo("Step 1");
        assertThat(state.todos()).singleElement().satisfies(todo -> {
            assertThat(todo.content()).isEqualTo("Do X");
            assertThat(todo.status()).isEqualTo("pending");
        });
    }

    @Test
    void blocksNonReadOnlyToolsWhilePlanning() {
        var executed = new java.util.concurrent.atomic.AtomicBoolean();
        var tools = new HarnessToolkit().register(
                new AiTool("echo", "Echo", JsonUtils.mapper().createObjectNode().put("type", "object"), null),
                (request, arguments) -> {
                    executed.set(true);
                    return new HarnessToolkit.ToolResult("should not run", false);
                });
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "plan_enter", "{}")))
                .thenReturn(response(toolCall("2", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(tools)
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .planModeEnabled(true)
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-blocked")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        assertThat(executed).isFalse();
    }

    @Test
    void evictsLargeToolResultsAndReadsThemBack() {
        var store = new ToolResultStore() {
            @Override
            public Mono<String> save(String runId, String toolName, String output) {
                return Mono.just("evicted-1");
            }

            @Override
            public Mono<String> read(String id) {
                return "evicted-1".equals(id) ? Mono.just("full output") : Mono.empty();
            }
        };
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(toolCall("2", "read_tool_result", "{\"id\":\"evicted-1\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var stateStore = new InMemoryAgentStateStore();
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .stateStore(stateStore)
                .toolResultEviction(new ToolResultEvictionConfig(2))
                .toolResultStore(store)
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-evict")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var state = stateStore.load("user-1", "session-evict").block();
        assertThat(state).isNotNull();
        assertThat(state.messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .extracting(ToolExecutionResultMessage::text)
                .anyMatch(text -> text.contains("已卸载"))
                .anyMatch(text -> text.equals("full output"));
    }

    @Test
    void resumesAnAskAfterConfirmation() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .build();
        var run = agent.start("run", RuntimeContext.of("user-1", "session-confirm"));

        run.confirm("1", true);
        assertThat(run.result().block().content()).isEqualTo("done");

        var result = run.events()
                .filter(event -> event.type() == AgentEventType.TOOL_RESULT_END)
                .cast(AgentEvent.ToolResultEnd.class)
                .blockFirst();
        assertThat(result).isNotNull();
        assertThat(result.output()).isEqualTo("echo:hello");
        assertThat(result.error()).isFalse();
    }

    @Test
    void executesToolsExternallyWhenConfigured() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .externalToolPatterns(List.of("echo"))
                .build();
        var run = agent.start("run", RuntimeContext.of("user-1", "session-external"));
        run.submitExternalResult("1", "external-output", false);

        assertThat(run.result().block(Duration.ofSeconds(3)).content()).isEqualTo("done");
        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, org.mockito.Mockito.times(2)).chat(requests.capture());
        assertThat(requests.getAllValues().get(1).messages())
                .anyMatch(message -> message instanceof ToolExecutionResultMessage result
                        && result.id().equals("1")
                        && result.text().equals("external-output")
                        && !result.isError());
        var events = run.events()
                .takeUntil(event -> event.type() == AgentEventType.AGENT_END)
                .collectList()
                .block(Duration.ofSeconds(3));
        assertThat(events).anyMatch(AgentEvent.RequireExternalExecution.class::isInstance);
        assertThat(events).anyMatch(AgentEvent.ExternalExecutionResult.class::isInstance);
    }

    @Test
    void timesOutExternalExecution() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .externalToolPatterns(List.of("echo"))
                .confirmationTimeout(Duration.ofMillis(50))
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-external-timeout")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, org.mockito.Mockito.times(2)).chat(requests.capture());
        assertThat(requests.getAllValues().get(1).messages())
                .anyMatch(message -> message instanceof ToolExecutionResultMessage result
                        && result.isError()
                        && result.text().contains("超时"));
    }

    @Test
    void searchesToolsThroughTheMetaTool() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "tool_search", "{\"query\":\"echo\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var stateStore = new InMemoryAgentStateStore();
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .stateStore(stateStore)
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-tool-search")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var state = stateStore.load("user-1", "session-tool-search").block();
        assertThat(state).isNotNull();
        assertThat(state.messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .map(ToolExecutionResultMessage.class::cast)
                .anySatisfy(message -> {
                    assertThat(message.id()).isEqualTo("1");
                    assertThat(message.text()).contains("echo");
                });
    }

    @Test
    void emitsSubagentExposedWhenDelegating() {
        var runtime = new SubagentRuntime()
                .register("helper", parent -> new EchoSubagent("helper"));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall(
                        "1", "agent_spawn", "{\"agent_id\":\"helper\",\"task\":\"hi\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .subagentTools(new SubagentTools(runtime))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .build();
        var run = agent.start("run", RuntimeContext.of("user-1", "session-subagent"));

        assertThat(run.result().block(Duration.ofSeconds(3)).content()).isEqualTo("done");
        var events = run.events()
                .takeUntil(event -> event.type() == AgentEventType.AGENT_END)
                .collectList()
                .block(Duration.ofSeconds(3));
        assertThat(events)
                .filteredOn(AgentEvent.SubagentExposed.class::isInstance)
                .map(AgentEvent.SubagentExposed.class::cast)
                .extracting(AgentEvent.SubagentExposed::subagentName)
                .contains("helper");
    }

    @Test
    void closesDanglingToolCallsBeforeResuming() {
        var seeded = new AgentState();
        seeded.addMessage(UserMessage.from("old request"));
        seeded.addMessage(toolCall("stale-1", "echo", "{\"text\":\"old\"}"));
        var stateStore = new InMemoryAgentStateStore();
        stateStore.save("user-1", "session-dangling", seeded).block();
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .stateStore(stateStore)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .build();

        StepVerifier.create(agent.call("next", RuntimeContext.of("user-1", "session-dangling")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel).chat(requests.capture());
        assertThat(requests.getValue().messages())
                .anyMatch(message -> message instanceof ToolExecutionResultMessage result
                        && result.id().equals("stale-1")
                        && result.isError()
                        && result.text().contains("中断"));
    }

    @Test
    void deniesAnUnansweredConfirmationAfterTheTimeout() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"hello\"}")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.builder()
                        .ask("echo")
                        .build()))
                .confirmationTimeout(Duration.ofMillis(50))
                .build();

        StepVerifier.create(agent.call("run", RuntimeContext.of("user-1", "session-confirm-timeout")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var requests = ArgumentCaptor.forClass(ChatRequest.class);
        verify(chatModel, org.mockito.Mockito.times(2)).chat(requests.capture());
        assertThat(requests.getAllValues().get(1).messages())
                .anyMatch(message -> message instanceof ToolExecutionResultMessage result
                        && result.isError()
                        && result.text().contains("用户拒绝"));
    }

    @Test
    void compactsAndRetriesOnceWhenTheContextOverflows() {
        var seeded = new AgentState();
        seeded.addMessage(UserMessage.from("old one"));
        seeded.addMessage(AiMessage.from("old two"));
        var stateStore = new InMemoryAgentStateStore();
        stateStore.save("user-1", "session-overflow", seeded).block();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenThrow(new IllegalStateException("maximum context length exceeded"))
                .thenReturn(response(AiMessage.from("summary")))
                .thenReturn(response(AiMessage.from("done")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .stateStore(stateStore)
                .compaction(new CompactionConfig(10, 1))
                .build();

        StepVerifier.create(agent.call("hello", RuntimeContext.of("user-1", "session-overflow")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        var state = stateStore.load("user-1", "session-overflow").block();
        assertThat(state).isNotNull();
        assertThat(state.summary()).contains("summary");
    }

    @Test
    void savesVersionedStateWithCompareAndSet() {
        when(versionedStateStore.loadVersioned("user-1", "session-cas"))
                .thenReturn(Mono.just(new VersionedAgentState(new AgentState(), 3L)));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response(AiMessage.from("done")));
        when(versionedStateStore.saveIfVersion(
                        eq("user-1"), eq("session-cas"), any(), eq(3L)))
                .thenReturn(Mono.just(4L));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .stateStore(versionedStateStore)
                .build();

        StepVerifier.create(agent.call("hello", RuntimeContext.of("user-1", "session-cas")))
                .assertNext(result -> assertThat(result.content()).isEqualTo("done"))
                .verifyComplete();

        verify(versionedStateStore)
                .saveIfVersion(eq("user-1"), eq("session-cas"), any(), eq(3L));
    }

    @Test
    void rejectsAConflictingStateVersion() {
        when(versionedStateStore.loadVersioned("user-1", "session-conflict"))
                .thenReturn(Mono.just(new VersionedAgentState(new AgentState(), 5L)));
        when(chatModel.chat(any(ChatRequest.class))).thenReturn(response(AiMessage.from("done")));
        when(versionedStateStore.saveIfVersion(
                        eq("user-1"), eq("session-conflict"), any(), eq(5L)))
                .thenReturn(Mono.empty());
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .stateStore(versionedStateStore)
                .build();

        StepVerifier.create(agent.call("hello", RuntimeContext.of("user-1", "session-conflict")))
                .expectError(AgentStateConflictException.class)
                .verify();
    }

    @Test
    void rejectsRunsThatExceedMaxIterations() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(response(toolCall("1", "echo", "{\"text\":\"loop\"}")));
        var agent = ReActAgent.builder()
                .name("test-agent")
                .chatModel(chatModel)
                .toolkit(new HarnessToolkit().register(new EchoTools()))
                .permissionEngine(new PermissionEngine(PermissionContext.bypass()))
                .maxIters(2)
                .build();

        StepVerifier.create(agent.call("hello", RuntimeContext.of("user-1", "session-4")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("最大迭代次数"))
                .verify();
    }

    private static ChatResponse response(AiMessage message) {
        return ChatResponse.builder().aiMessage(message).build();
    }

    private static AiMessage toolCall(String id, String name, String arguments) {
        return AiMessage.builder()
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id(id)
                        .name(name)
                        .arguments(arguments)
                        .build()))
                .build();
    }

    private static final class EchoTools {

        @Tool(name = "echo", value = "Echo text")
        String echo(@P("text") String text) {
            return "echo:" + text;
        }
    }

    private static final class EchoSubagent implements Agent {

        private final String name;

        private EchoSubagent(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public Mono<AgentResult> call(String input, RuntimeContext context) {
            return start(input, context).result();
        }

        @Override
        public AgentRun start(String input, RuntimeContext context) {
            var run = new AgentRun("run-" + UUID.randomUUID());
            run.attach(Mono.fromSupplier(() -> new AgentResult(name + ":" + input, null))
                    .doOnSubscribe(subscription ->
                            run.emit(new AgentEvent.AgentStart(run.id(), name)))
                    .doFinally(signal -> run.emit(new AgentEvent.AgentEnd(run.id(), name))));
            return run;
        }
    }
}
