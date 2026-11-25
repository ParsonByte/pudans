package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.agent.tool.GenerateMediaTool;
import cn.tolim.pudans.core.agent.memory.AgentMemoryContext;
import cn.tolim.pudans.core.ai.quota.AiPricing;
import cn.tolim.pudans.core.ai.quota.AiQuotas;
import cn.tolim.pudans.infra.r2dbc.agent.AgentApprovalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentMessageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentRunRepository;
import cn.tolim.pudans.infra.r2dbc.agent.AgentToolCallRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentScopeRuntimeTest {

    @Mock
    AgentModelRouteResolver routes;

    @Mock
    GenerateMediaTool mediaTool;

    @Mock
    AgentRunEvents events;

    @Mock
    AgentRunRepository runs;

    @Mock
    AgentMessageRepository messages;

    @Mock
    AgentToolCallRepository toolCalls;

    @Mock
    AgentApprovalRepository approvals;

    @Mock
    AiQuotas quotas;

    @Mock
    AiPricing pricing;

    @Mock
    AgentChatModelFactory modelFactory;

    @Mock
    AgentMemoryContext memoryContext;

    private AgentScopeRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new AgentScopeRuntime(
                routes,
                mediaTool,
                events,
                runs,
                messages,
                toolCalls,
                approvals,
                quotas,
                pricing,
                modelFactory,
                memoryContext);
    }

    @Test
    void cancelReturnsFalseWhenNoLocalHandleExists() {
        StepVerifier.create(runtime.cancel(context()))
                .expectNext(false)
                .verifyComplete();

        verifyNoInteractions(runs, events, quotas);
    }

    @Test
    void resumeReturnsFalseWhenNoLocalApprovalExists() {
        StepVerifier.create(runtime.resume(context(), UUID.randomUUID(), true))
                .expectNext(false)
                .verifyComplete();

        verifyNoInteractions(runs, events, quotas);
    }

    @Test
    void propagatesModelRouteFailuresBeforeReservingQuota() {
        var context = context();
        when(routes.resolve(context.workspaceId(), context.modelId()))
                .thenReturn(Mono.error(new IllegalStateException("route unavailable")));

        StepVerifier.create(runtime.start(context))
                .expectErrorSatisfies(error -> assertThat(error)
                        .hasMessage("route unavailable"))
                .verify();

        verifyNoInteractions(runs, events, quotas);
    }

    private static AgentRunContext context() {
        return new AgentRunContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "alice",
                "workspace-a",
                UUID.randomUUID(),
                null,
                null,
                List.of(),
                Set.of("agent-user"),
                "hello");
    }
}
