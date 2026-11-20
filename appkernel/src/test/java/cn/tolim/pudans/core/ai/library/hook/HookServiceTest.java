package cn.tolim.pudans.core.ai.library.hook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiHook;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.runtime.AgentRuntimeService;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxExecResult;
import cn.tolim.pudans.core.webhook.WebhookEventPublisher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class HookServiceTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    WebhookEventPublisher eventPublisher;

    @InjectMocks
    HookService service;

    @Test
    void dispatchesMatchingHooksThroughTheEventBus() {
        var hook = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read");
        when(client.listAll(eq(AiHook.class), any(), any())).thenReturn(Flux.just(hook));
        Map<String, Object> payload = Map.of("tool", "ReadFile", "traceFlowId", "trace-1");
        Map<String, Object> expected = Map.of(
                "agent", "agent-a",
                "event", "PRE_TOOL_USE",
                "hook", "read-hook",
                "command", "notify",
                "timeoutSeconds", 60,
                "payload", payload);

        StepVerifier.create(service.dispatch("agent-a", AiHook.Event.PRE_TOOL_USE, payload))
                .verifyComplete();

        verify(eventPublisher).publish("ai.hook.PRE_TOOL_USE", expected, "trace-1");
    }

    @Test
    void executesHookCommandsInTheAgentSandbox() {
        var hook = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read");
        var runtime = mock(AgentRuntimeService.class);
        var sandboxed = new HookService(client, eventPublisher, runtime);
        when(client.listAll(eq(AiHook.class), any(), any())).thenReturn(Flux.just(hook));
        when(client.get(AiAgent.class, "agent-a")).thenReturn(Mono.just(agentWithRuntime()));
        when(runtime.execInternal(eq("agent-a"), eq("notify"), isNull(), eq(60)))
                .thenReturn(Mono.just(new SandboxExecResult(0, "", "", 1)));

        StepVerifier.create(sandboxed.dispatch(
                        "agent-a", AiHook.Event.PRE_TOOL_USE, Map.of("tool", "ReadFile")))
                .verifyComplete();

        verify(runtime).execInternal("agent-a", "notify", null, 60);
    }

    @Test
    void skipsHooksWhoseMatcherDoesNotMatchTheTool() {
        var hook = hook("bash-hook", AiHook.Event.PRE_TOOL_USE, "Bash*");
        when(client.listAll(eq(AiHook.class), any(), any())).thenReturn(Flux.just(hook));

        StepVerifier.create(service.dispatch(
                        "agent-a", AiHook.Event.PRE_TOOL_USE, Map.of("tool", "ReadFile")))
                .verifyComplete();

        verify(eventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void swallowsDispatchFailures() {
        when(client.listAll(eq(AiHook.class), any(), any()))
                .thenReturn(Flux.error(new IllegalStateException("boom")));

        StepVerifier.create(service.dispatch("agent-a", AiHook.Event.SESSION_START, null))
                .verifyComplete();
    }

    @Test
    void matchingKeepsOnlyHooksForTheToolName() {
        var readHook = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read");
        var bashHook = hook("bash-hook", AiHook.Event.PRE_TOOL_USE, "Bash*");
        when(client.listAll(eq(AiHook.class), any(), any())).thenReturn(Flux.just(readHook, bashHook));

        StepVerifier.create(service.matching("agent-a", AiHook.Event.PRE_TOOL_USE, "ReadFile"))
                .expectNext(readHook)
                .verifyComplete();
    }

    @Test
    void wildcardMatcherAcceptsEveryToolName() {
        var spec = hook("any-tool", AiHook.Event.POST_TOOL_USE, "*").getSpec();

        assertThat(HookService.matches(spec, AiHook.Event.POST_TOOL_USE, "Bash")).isTrue();
        assertThat(HookService.matches(spec, AiHook.Event.POST_TOOL_USE, null)).isTrue();
    }

    @Test
    void plainMatcherRequiresTheToolNamePrefix() {
        var spec = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read").getSpec();

        assertThat(HookService.matches(spec, AiHook.Event.PRE_TOOL_USE, "ReadFile")).isTrue();
        assertThat(HookService.matches(spec, AiHook.Event.PRE_TOOL_USE, "WriteFile")).isFalse();
    }

    @Test
    void matcherIgnoresNonToolEvents() {
        var spec = hook("session-hook", AiHook.Event.SESSION_START, "Bash*").getSpec();

        assertThat(HookService.matches(spec, AiHook.Event.SESSION_START, null)).isTrue();
    }

    @Test
    void matcherRequiresAToolNameForToolEvents() {
        var spec = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read").getSpec();

        assertThat(HookService.matches(spec, AiHook.Event.PRE_TOOL_USE, null)).isFalse();
    }

    @Test
    void blankMatcherAcceptsEveryToolName() {
        var spec = hook("all-tools", AiHook.Event.PRE_TOOL_USE, " ").getSpec();

        assertThat(HookService.matches(spec, AiHook.Event.PRE_TOOL_USE, "WriteFile")).isTrue();
    }

    @Test
    void validateAcceptsAValidHook() {
        service.validate(hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read"));
    }

    @Test
    void validateRejectsABlankDisplayName() {
        var hook = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read");
        hook.getSpec().setDisplayName(" ");

        assertThatThrownBy(() -> service.validate(hook))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void validateRejectsAMissingEvent() {
        var hook = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read");
        hook.getSpec().setEvent(null);

        assertThatThrownBy(() -> service.validate(hook))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void validateRejectsABlankCommand() {
        var hook = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read");
        hook.getSpec().setCommand("");

        assertThatThrownBy(() -> service.validate(hook))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void validateRejectsAnOutOfRangeTimeout() {
        var hook = hook("read-hook", AiHook.Event.PRE_TOOL_USE, "Read");
        hook.getSpec().setTimeoutSeconds(0);

        assertThatThrownBy(() -> service.validate(hook))
                .isInstanceOf(ServerWebInputException.class);

        hook.getSpec().setTimeoutSeconds(601);

        assertThatThrownBy(() -> service.validate(hook))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void byAgentRejectsABlankAgentName() {
        assertThatThrownBy(() -> service.byAgent(" ", null))
                .isInstanceOf(ServerWebInputException.class);
    }

    private static AiAgent agentWithRuntime() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("agent-a");
        agent.setMetadata(metadata);
        agent.getSpec().getRuntime().setProvider(AiAgent.RuntimeProvider.ALIYUN);
        return agent;
    }

    private static AiHook hook(String name, AiHook.Event event, String matcher) {
        var hook = new AiHook();
        var metadata = new Metadata();
        metadata.setName(name);
        hook.setMetadata(metadata);
        hook.getSpec().setDisplayName("Hook " + name);
        hook.getSpec().setEvent(event);
        hook.getSpec().setMatcher(matcher);
        hook.getSpec().setCommand("notify");
        hook.getSpec().getAgents().add("agent-a");
        return hook;
    }
}