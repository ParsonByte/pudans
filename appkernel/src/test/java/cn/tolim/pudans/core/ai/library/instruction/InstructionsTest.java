package cn.tolim.pudans.core.ai.library.instruction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiInstruction;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class InstructionsTest {

    @Mock
    ReactiveExtensionClient client;

    Instructions service;

    @BeforeEach
    void setUp() {
        service = new Instructions(client);
    }

    @Test
    void includesAllSharedInstructionsAndAgentBoundOnes() {
        var shared = instruction("shared-a", AiInstruction.Scope.SHARED, true, "agent-b");
        var disabled = instruction("disabled", AiInstruction.Scope.SHARED, false, "agent-b");
        var bound = instruction("bound-a", AiInstruction.Scope.AGENT, true, "agent-a");
        var otherAgent = instruction("other", AiInstruction.Scope.AGENT, true, "agent-b");
        when(client.listAll(eq(AiInstruction.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound, otherAgent, shared, disabled));

        StepVerifier.create(service.byAgent("agent-a"))
                .assertNext(instruction -> assertThat(instruction.getMetadata().getName())
                        .isEqualTo("shared-a"))
                .assertNext(instruction -> assertThat(instruction.getMetadata().getName())
                        .isEqualTo("bound-a"))
                .verifyComplete();
    }

    @Test
    void joinsSystemSectionsSharedFirstThenAgentBoundInNameOrder() {
        var sharedB = instruction("shared-b", AiInstruction.Scope.SHARED, true, "agent-b");
        var sharedA = instruction("shared-a", AiInstruction.Scope.SHARED, true, "agent-b");
        var bound = instruction("bound-a", AiInstruction.Scope.AGENT, true, "agent-a");
        sharedA.getSpec().setContent("共享规则 A");
        sharedB.getSpec().setContent("共享规则 B");
        bound.getSpec().setContent("Agent 规则");
        when(client.listAll(eq(AiInstruction.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(bound, sharedB, sharedA));

        StepVerifier.create(service.systemSections("agent-a"))
                .assertNext(sections -> assertThat(sections).isEqualTo("共享规则 A\n\n共享规则 B\n\nAgent 规则"))
                .verifyComplete();
    }

    @Test
    void returnsAnEmptySectionWhenNothingApplies() {
        when(client.listAll(eq(AiInstruction.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.systemSections("agent-a"))
                .assertNext(sections -> assertThat(sections).isEmpty())
                .verifyComplete();
    }

    @Test
    void skipsBlankContent() {
        var blank = instruction("blank", AiInstruction.Scope.SHARED, true, "agent-b");
        blank.getSpec().setContent("   ");
        when(client.listAll(eq(AiInstruction.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(blank));

        StepVerifier.create(service.systemSections("agent-a"))
                .assertNext(sections -> assertThat(sections).isEmpty())
                .verifyComplete();
    }

    @Test
    void requiresDisplayNameContentAndScope() {
        var instruction = new AiInstruction();
        instruction.getSpec().setDisplayName("broken");
        instruction.getSpec().setContent(null);

        StepVerifier.create(Instructions.validate(instruction))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    var apiException = (ApiException) error;
                    assertThat(apiException.getStatusCode().value()).isEqualTo(400);
                    assertThat(apiException.getCode()).isEqualTo(Instructions.CODE_INVALID);
                    assertThat(apiException.getReason()).contains("content");
                })
                .verify();
    }

    private static AiInstruction instruction(
            String name, AiInstruction.Scope scope, boolean enabled, String agent) {
        var instruction = new AiInstruction();
        var metadata = new Metadata();
        metadata.setName(name);
        instruction.setMetadata(metadata);
        instruction.getSpec().setDisplayName(name);
        instruction.getSpec().setContent(name);
        instruction.getSpec().setScope(scope);
        instruction.getSpec().setEnabled(enabled);
        instruction.getSpec().getAgents().add(agent);
        return instruction;
    }
}