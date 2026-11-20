package cn.tolim.pudans.core.ai.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiHook;
import cn.tolim.pudans.contract.core.extension.ai.AiSubagent;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiGatewayInvocation;
import cn.tolim.pudans.core.ai.gateway.AiGatewayMeta;
import cn.tolim.pudans.core.ai.gateway.AiGatewayResult;
import cn.tolim.pudans.core.ai.gateway.AiGatewayService;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.library.LibraryView;
import cn.tolim.pudans.core.ai.library.hook.HookService;
import cn.tolim.pudans.core.ai.library.skill.SkillService;
import cn.tolim.pudans.core.ai.library.subagent.SubagentService;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimeService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AiChatToolServiceSubagentTest {

    private final LibraryView libraryView = mock(LibraryView.class);

    private final SkillService skillService = mock(SkillService.class);

    private final SubagentService subagentService = mock(SubagentService.class);

    private final HookService hookService = mock(HookService.class);

    private final AiGatewayService gatewayService = mock(AiGatewayService.class);

    private final AgentRuntimeService runtimeService = mock(AgentRuntimeService.class);

    private final AiChatToolService service = new AiChatToolService(
            libraryView, skillService, subagentService, hookService, gatewayService, runtimeService);

    @Test
    void runsSubagentsThroughLangChain4jAiServices() {
        var captured = new AtomicReference<AiGatewayInvocation>();
        when(hookService.dispatch(any(), any(AiHook.Event.class), any())).thenReturn(Mono.empty());
        when(subagentService.resolve("agent-a", "helper")).thenReturn(Mono.just(subagent()));
        when(gatewayService.complete(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return Mono.just(result());
        });
        var arguments = JsonUtils.mapper().createObjectNode()
                .put("name", "helper")
                .put("prompt", "summarize");

        var outcome = service
                .execute(
                        "user-1",
                        agent(),
                        new AiContentPart.ToolCall("call-1", "subagent", arguments, null))
                .block();

        assertThat(outcome).isNotNull();
        assertThat(outcome.output()).isEqualTo("sub-answer");
        assertThat(outcome.isError()).isFalse();
        var invocation = captured.get();
        assertThat(invocation).isNotNull();
        assertThat(invocation.userId()).isEqualTo("user-1");
        assertThat(invocation.agentSource()).isEqualTo("pudans-subagent");
        assertThat(invocation.agentName()).isEqualTo("agent-a");
        assertThat(invocation.routeGroupCode()).isEqualTo("fast");
        assertThat(invocation.modelOverride()).isEqualTo("gpt-x");
    }

    private static AiAgent agent() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("agent-a");
        agent.setMetadata(metadata);
        return agent;
    }

    private static AiSubagent subagent() {
        var subagent = new AiSubagent();
        subagent.getSpec().setPrompt("You are a helper.");
        subagent.getSpec().setModel("gpt-x");
        subagent.getSpec().setRouteGroup("fast");
        return subagent;
    }

    private static AiGatewayResult result() {
        var response = new AiChatResponse(
                "resp-1",
                "gpt-x",
                new AiChatMessage(
                        "assistant", List.of(new AiContentPart.Text("sub-answer", null))),
                new AiTokenUsage(1, 2, null, null),
                "stop");
        return new AiGatewayResult(
                response,
                new AiGatewayMeta(
                        "sub-1", 1, "chat", "fast", "openai", "account-1", "gpt-x",
                        null, "team-1", "agent-a", "session-1"),
                new AiTokenUsage(1, 2, null, null),
                BigDecimal.ONE,
                "wallet");
    }
}