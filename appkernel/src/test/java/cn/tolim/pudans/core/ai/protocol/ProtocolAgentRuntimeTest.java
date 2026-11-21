package cn.tolim.pudans.core.ai.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiAgent;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.chat.AgentContext;
import cn.tolim.pudans.core.ai.chat.ChatTools;
import cn.tolim.pudans.core.ai.chat.SubagentToolFactory;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGateway;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.harness.agent.InMemoryAgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.journal.InMemoryThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.observability.HarnessObservability;
import cn.tolim.pudans.core.ai.harness.journal.JournalEvent;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventType;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentRuntime;
import cn.tolim.pudans.core.ai.harness.subagent.SubagentTools;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkit;
import cn.tolim.pudans.core.ai.harness.tool.HarnessToolkitFactory;
import cn.tolim.pudans.core.ai.library.mcp.McpServers;
import cn.tolim.pudans.core.ai.team.Teams;
import dev.langchain4j.data.message.UserMessage;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProtocolAgentRuntimeTest {

    @Mock
    Teams teams;

    @Mock
    AgentContext agentContext;

    @Mock
    ChatTools chatTools;

    @Mock
    SubagentToolFactory subagentToolFactory;

    @Mock
    HarnessToolkitFactory harnessToolkitFactory;

    @Mock
    McpServers mcpServers;

    @Mock
    HarnessObservability harnessObservability;

    @Mock
    AiGateway aiGateway;

    @Test
    void runsTheAgentWithPersistentStateAndJournal() {
        var agent = agent();
        when(teams.requireAgent("agent-a", "user-1")).thenReturn(Mono.just(agent));
        when(agentContext.build(agent)).thenReturn(Mono.just(new AgentContext.Context(
                "", List.of(), List.of(), List.of(), List.of(), Map.of())));
        when(subagentToolFactory.create(any(), any())).thenReturn(
                Mono.just(new SubagentTools(new SubagentRuntime())));
        when(harnessToolkitFactory.create(any(), any(), anyBoolean(), any(), any()))
                .thenReturn(new HarnessToolkit());
        when(mcpServers.decorateToolkit(any(), any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(aiGateway.complete(any())).thenReturn(Mono.just(gatewayResult()));
        var stateStore = new InMemoryAgentStateStore();
        var journalStore = new InMemoryThreadJournalStore();
        var runtime = new ProtocolAgentRuntime(
                teams,
                agentContext,
                chatTools,
                subagentToolFactory,
                harnessToolkitFactory,
                mcpServers,
                harnessObservability,
                aiGateway,
                stateStore,
                journalStore);

        var run = runtime.start(new ProtocolAgentRuntime.ProtocolRunRequest(
                        "user-1", "agent-a", "thread-1", null, UserMessage.from("你好"), false, Map.of()))
                .block();

        assertThat(run).isNotNull();
        StepVerifier.create(run.result())
                .assertNext(result -> assertThat(result.content()).isEqualTo("回答"))
                .verifyComplete();

        var state = stateStore.load("user-1", "thread-1").block();
        assertThat(state).isNotNull();
        assertThat(state.messages()).isNotEmpty();

        var events = journalStore
                .open(RuntimeContext.of("user-1", "thread-1"))
                .flatMapMany(journal -> journal.scan(0, Long.MAX_VALUE))
                .collectList()
                .block();
        assertThat(events).extracting(JournalEvent::type).contains(
                JournalEventType.RUN_START,
                JournalEventType.RUN_OUTPUT,
                JournalEventType.STATE_CHECKPOINT,
                JournalEventType.RUN_END);
    }

    private static AiAgent agent() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("agent-a");
        agent.setMetadata(metadata);
        agent.getSpec().setPrompt("你是协议助手");
        return agent;
    }

    private static AiGatewayResult gatewayResult() {
        var usage = new AiTokenUsage(1, 2, null, null);
        return new AiGatewayResult(
                new AiChatResponse(
                        "resp-1",
                        "gpt-test",
                        new AiChatMessage("assistant", List.of(new AiContentPart.Text("回答", null))),
                        usage,
                        "stop"),
                new AiGatewayMeta(
                        "req-1",
                        0L,
                        null,
                        null,
                        "provider-a",
                        "account-a",
                        "gpt-test",
                        null,
                        null,
                        "agent-a",
                        "thread-1"),
                usage,
                BigDecimal.ONE,
                "PROVIDER");
    }
}