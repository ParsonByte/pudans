package cn.tolim.pudans.core.ai.library.environment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiEnvironmentVariable;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
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
class EnvironmentVariablesTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    EnvironmentVariables service;

    @Test
    void createRejectsNamesThatDoNotMatchTheEnvPattern() {
        for (var name : Arrays.asList("feishu_token", "1TOKEN", "FEISHU-TOKEN", "FEISHU TOKEN", null)) {
            assertThatThrownBy(() -> service.create(variable("env-1", name, "secret")))
                    .as("name [%s]", name)
                    .isInstanceOf(ServerWebInputException.class);
        }
    }

    @Test
    void createMarksAValueAsConfigured() {
        when(client.create(any(AiEnvironmentVariable.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var variable = variable("env-1", "FEISHU_TOKEN", "secret");

        StepVerifier.create(service.create(variable))
                .expectNext(variable)
                .verifyComplete();

        assertThat(variable.getSpec().isConfigured()).isTrue();
    }

    @Test
    void createWithoutAValueLeavesTheVariableUnconfigured() {
        when(client.create(any(AiEnvironmentVariable.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var variable = variable("env-1", "FEISHU_TOKEN", null);

        StepVerifier.create(service.create(variable))
                .expectNext(variable)
                .verifyComplete();

        assertThat(variable.getSpec().isConfigured()).isFalse();
    }

    @Test
    void maskNeverExposesTheValue() {
        var components = EnvironmentVariables.MaskedEnvironmentVariable.class.getRecordComponents();

        assertThat(Arrays.stream(components).map(RecordComponent::getName))
                .containsExactly("resourceName", "name", "configured", "description", "enabled", "agents");
    }

    @Test
    void maskedByAgentReportsConfiguredWithoutTheValue() {
        var variable = variable("env-1", "FEISHU_TOKEN", "secret");
        when(client.listAll(eq(AiEnvironmentVariable.class), any(), any())).thenReturn(Flux.just(variable));

        StepVerifier.create(service.maskedByAgent("agent-a"))
                .assertNext(masked -> {
                    assertThat(masked.name()).isEqualTo("FEISHU_TOKEN");
                    assertThat(masked.configured()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void resolveValuesSkipsVariablesWithoutAValue() {
        when(client.listAll(eq(AiEnvironmentVariable.class), any(), any()))
                .thenReturn(Flux.just(
                        variable("env-1", "ALPHA", "a"),
                        variable("env-2", "BETA", " "),
                        variable("env-3", "GAMMA", "b")));

        StepVerifier.create(service.resolveValues("agent-a"))
                .expectNext(Map.of("ALPHA", "a", "GAMMA", "b"))
                .verifyComplete();
    }

    @Test
    void updatePreservesTheStoredValueWhenTheRequestCarriesNoValue() {
        var existing = variable("env-1", "FEISHU_TOKEN", "old-secret");
        existing.getSpec().setConfigured(true);
        when(client.get(AiEnvironmentVariable.class, "env-1")).thenReturn(Mono.just(existing));
        when(client.update(any(AiEnvironmentVariable.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var requested = variable("env-1", "FEISHU_TOKEN", null);

        StepVerifier.create(service.update(requested))
                .expectNext(requested)
                .verifyComplete();

        assertThat(requested.getSpec().getValue()).isEqualTo("old-secret");
        assertThat(requested.getSpec().isConfigured()).isTrue();
    }

    @Test
    void updateClearsTheValueWhenConfiguredIsFalse() {
        var existing = variable("env-1", "FEISHU_TOKEN", "old-secret");
        when(client.get(AiEnvironmentVariable.class, "env-1")).thenReturn(Mono.just(existing));
        when(client.update(any(AiEnvironmentVariable.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var requested = variable("env-1", "FEISHU_TOKEN", "ignored");
        requested.getSpec().setConfigured(false);

        StepVerifier.create(service.update(requested))
                .expectNext(requested)
                .verifyComplete();

        assertThat(requested.getSpec().getValue()).isNull();
        assertThat(requested.getSpec().isConfigured()).isFalse();
    }

    @Test
    void updateReplacesTheValueWhenAValueIsPresent() {
        var existing = variable("env-1", "FEISHU_TOKEN", "old-secret");
        when(client.get(AiEnvironmentVariable.class, "env-1")).thenReturn(Mono.just(existing));
        when(client.update(any(AiEnvironmentVariable.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var requested = variable("env-1", "FEISHU_TOKEN", "new-secret");

        StepVerifier.create(service.update(requested))
                .expectNext(requested)
                .verifyComplete();

        assertThat(requested.getSpec().getValue()).isEqualTo("new-secret");
        assertThat(requested.getSpec().isConfigured()).isTrue();
    }

    @Test
    void updateValueSetsTheValue() {
        var existing = variable("env-1", "FEISHU_TOKEN", "old-secret");
        when(client.get(AiEnvironmentVariable.class, "env-1")).thenReturn(Mono.just(existing));
        when(client.update(any(AiEnvironmentVariable.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.updateValue("env-1", "new-secret", false))
                .expectNext(existing)
                .verifyComplete();

        assertThat(existing.getSpec().getValue()).isEqualTo("new-secret");
        assertThat(existing.getSpec().isConfigured()).isTrue();
    }

    @Test
    void updateValueClearsTheValue() {
        var existing = variable("env-1", "FEISHU_TOKEN", "old-secret");
        when(client.get(AiEnvironmentVariable.class, "env-1")).thenReturn(Mono.just(existing));
        when(client.update(any(AiEnvironmentVariable.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.updateValue("env-1", null, true))
                .expectNext(existing)
                .verifyComplete();

        assertThat(existing.getSpec().getValue()).isNull();
        assertThat(existing.getSpec().isConfigured()).isFalse();
    }

    @Test
    void rejectsABlankAgentName() {
        assertThatThrownBy(() -> service.byAgent(" "))
                .isInstanceOf(ServerWebInputException.class);
    }

    @Test
    void maskKeepsTheAgentBindings() {
        var variable = variable("env-1", "FEISHU_TOKEN", "secret");

        var masked = EnvironmentVariables.mask(variable);

        assertThat(masked.resourceName()).isEqualTo("env-1");
        assertThat(masked.agents()).containsExactly("agent-a");
        assertThat(masked.enabled()).isTrue();
    }

    private static AiEnvironmentVariable variable(String resourceName, String name, String value) {
        var variable = new AiEnvironmentVariable();
        var metadata = new Metadata();
        metadata.setName(resourceName);
        variable.setMetadata(metadata);
        variable.getSpec().setName(name);
        variable.getSpec().setValue(value);
        variable.getSpec().setDescription("飞书令牌");
        variable.getSpec().setAgents(new java.util.ArrayList<>(List.of("agent-a")));
        return variable;
    }
}