package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiSubagent;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.agent.GatewayChatContext;
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
import cn.tolim.pudans.core.ai.harness.subagent.SubagentTools;
import cn.tolim.pudans.core.ai.library.mcp.McpServerService;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SubagentToolFactoryTest {

    @Mock
    AiGatewayService gatewayService;

    @Mock
    SkillService skillService;

    @Mock
    McpServerService mcpServerService;

    @Test
    void buildsSubagentToolsThatDelegateThroughTheGateway() {
        var factory = new SubagentToolFactory(
                gatewayService,
                new InMemoryAgentStateStore(),
                new InMemoryThreadJournalStore(),
                skillService,
                new cn.tolim.pudans.core.ai.harness.skill.curator.InMemorySkillUsageStore(),
                mcpServerService);
        when(skillService.byNames(any())).thenReturn(Mono.just(List.of()));
        when(mcpServerService.toolCatalogFor(any()))
                .thenReturn(Mono.just(new McpServerService.McpToolCatalog(List.of())));
        when(gatewayService.complete(any())).thenReturn(Mono.just(gatewayResult()));
        var tools = factory.create(context(), List.of(subagent())).block();

        assertThat(tools).isNotNull();
        assertThat(tools.definitions()).extracting(tool -> tool.name())
                .contains(SubagentTools.AGENT_SPAWN, SubagentTools.AGENT_TASK);
        ObjectNode arguments = JsonUtils.mapper().createObjectNode()
                .put("agent_id", "helper")
                .put("task", "hi");
        StepVerifier.create(tools.execute(
                        SubagentTools.AGENT_SPAWN,
                        arguments,
                        RuntimeContext.of("user-1", "session-1")))
                .assertNext(result -> {
                    assertThat(result.error()).isFalse();
                    assertThat(result.output()).contains("\"result\":\"回答\"");
                })
                .verifyComplete();
    }

    private static GatewayChatContext context() {
        return new GatewayChatContext(
                "user-1", "pudans-chat", "session-1", null, "req-1", "idem-1",
                "fast", "fast-group", "agent-a", 7L, null);
    }

    private static AiSubagent subagent() {
        var subagent = new AiSubagent();
        var metadata = new Metadata();
        metadata.setName("helper");
        subagent.setMetadata(metadata);
        subagent.getSpec().setPrompt("你是助手");
        return subagent;
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
                        "req-1", 0L, "fast", "fast-group", "provider-a", "account-a",
                        "gpt-test", null, null, "helper", "session-1"),
                usage,
                BigDecimal.ONE,
                "PROVIDER");
    }
}