package cn.tolim.pudans.core.ai.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.chat.AiAgentContextService;
import cn.tolim.pudans.core.ai.chat.AiChatToolService;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.harness.agent.InMemoryAgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.journal.InMemoryThreadJournalStore;
import cn.tolim.pudans.core.ai.harness.journal.JournalEvent;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventType;
import cn.tolim.pudans.core.ai.team.AiTeamService;
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
    AiTeamService teamService;

    @Mock
    AiAgentContextService agentContextService;

    @Mock
    AiChatToolService toolService;

    @Mock
    AiGatewayService gatewayService;

    @Test
    void runsTheAgentWithPersistentStateAndJournal() {
        var agent = agent();
        when(teamService.requireAgent("agent-a", "user-1")).thenReturn(Mono.just(agent));
        when(agentContextService.build(agent)).thenReturn(Mono.just(new AiAgentContextService.AgentContext(
                "", List.of(), List.of(), List.of(), List.of(), Map.of())));
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        var stateStore = new InMemoryAgentStateStore();
        var journalStore = new InMemoryThreadJournalStore();
        var runtime = new ProtocolAgentRuntime(
                teamService, agentContextService, toolService, gatewayService, stateStore, journalStore);

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