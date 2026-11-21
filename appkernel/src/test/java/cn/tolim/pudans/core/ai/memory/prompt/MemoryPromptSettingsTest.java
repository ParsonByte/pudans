package cn.tolim.pudans.core.ai.memory.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.contract.ai.AiMemoryPromptBinding;
import cn.tolim.pudans.contract.ai.AiTeam;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.memory.MemoryPromptResolver;
import cn.tolim.pudans.core.ai.memory.prompt.MemoryPromptSettings.SetCommand;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.memory.AiMemoryPromptLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryPromptSettingsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiMemoryPromptLogRepository logRepository;

    @Mock
    Teams teams;

    private MemoryPromptSettings service;

    @BeforeEach
    void setUp() {
        service = new MemoryPromptSettings(client, new MemoryPromptResolver(), logRepository, teams);
    }

    @Test
    void applyCreatesBindingAndWritesLog() {
        when(client.get(AiMemoryPrompt.class, "prompt-1")).thenReturn(Mono.just(prompt("prompt-1", 3)));
        when(client.get(AiMemoryPromptBinding.class, "memory-prompt-l1-instance"))
                .thenReturn(Mono.empty());
        when(client.create(any(AiMemoryPromptBinding.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(logRepository.insert(any(MemoryPromptLog.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.set(
                        "user-1",
                        new SetCommand(
                                "prompt-1",
                                AiMemoryPrompt.Layer.L1,
                                AiMemoryPromptBinding.TargetType.INSTANCE,
                                null,
                                null,
                                MemoryPromptLog.Action.APPLY)))
                .assertNext(log -> {
                    assertThat(log.promptId()).isEqualTo("prompt-1");
                    assertThat(log.version()).isEqualTo(3);
                    assertThat(log.action()).isEqualTo(MemoryPromptLog.Action.APPLY);
                    assertThat(log.targetType()).isEqualTo(AiMemoryPromptBinding.TargetType.INSTANCE);
                    assertThat(log.layer()).isEqualTo(AiMemoryPrompt.Layer.L1);
                    assertThat(log.actor()).isEqualTo("user-1");
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(AiMemoryPromptBinding.class);
        verify(client).create(captor.capture());
        var binding = captor.getValue();
        assertThat(binding.getMetadata().getName()).isEqualTo("memory-prompt-l1-instance");
        assertThat(binding.getSpec().getPrompt()).isEqualTo("prompt-1");
        assertThat(binding.getSpec().getTargetType()).isEqualTo(AiMemoryPromptBinding.TargetType.INSTANCE);
        assertThat(binding.getSpec().getLayer()).isEqualTo(AiMemoryPrompt.Layer.L1);
        assertThat(binding.getSpec().getUpdatedBy()).isEqualTo("user-1");
    }

    @Test
    void applyRejectsPromptFromAnotherLayer() {
        when(client.get(AiMemoryPrompt.class, "prompt-1")).thenReturn(Mono.just(prompt("prompt-1", 1)));

        StepVerifier.create(service.set(
                        "user-1",
                        new SetCommand(
                                "prompt-1",
                                AiMemoryPrompt.Layer.L2,
                                AiMemoryPromptBinding.TargetType.INSTANCE,
                                null,
                                null,
                                MemoryPromptLog.Action.APPLY)))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(MemoryPromptSettings.CODE_INVALID))
                .verify();
    }

    @Test
    void clearDeletesBindingAndLogsPreviousPrompt() {
        var bindingName =
                cn.tolim.pudans.core.ai.memory.asset.MemoryAssetNames.bindingName("L3", "TEAM", "team-a", null);
        var binding = binding(bindingName, "prompt-1", AiMemoryPrompt.Layer.L3);
        when(teams.getTeam("team-a")).thenReturn(Mono.just(team("team-a")));
        when(client.get(AiMemoryPromptBinding.class, bindingName))
                .thenReturn(Mono.just(binding));
        when(client.delete(binding)).thenReturn(Mono.just(binding));
        when(client.get(AiMemoryPrompt.class, "prompt-1")).thenReturn(Mono.just(prompt("prompt-1", 2)));
        when(logRepository.insert(any(MemoryPromptLog.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(service.set(
                        "admin-1",
                        new SetCommand(
                                "prompt-2",
                                AiMemoryPrompt.Layer.L3,
                                AiMemoryPromptBinding.TargetType.TEAM,
                                "team-a",
                                null,
                                MemoryPromptLog.Action.CLEAR)))
                .assertNext(log -> {
                    assertThat(log.action()).isEqualTo(MemoryPromptLog.Action.CLEAR);
                    assertThat(log.promptId()).isEqualTo("prompt-1");
                    assertThat(log.version()).isEqualTo(2);
                    assertThat(log.team()).isEqualTo("team-a");
                })
                .verifyComplete();

        verify(client).delete(binding);
    }

    @Test
    void settingsResolvesInstanceBinding() {
        when(client.listAll(eq(AiMemoryPromptBinding.class), any(), any()))
                .thenReturn(Flux.just(binding("memory-prompt-l1-instance", "prompt-1", AiMemoryPrompt.Layer.L1)));
        when(client.listAll(eq(AiMemoryPrompt.class), any(), any()))
                .thenReturn(Flux.just(prompt("prompt-1", 4)));

        StepVerifier.create(service.settings(AiMemoryPrompt.Layer.L1, null, null))
                .assertNext(resolved -> {
                    assertThat(resolved.promptId()).isEqualTo("prompt-1");
                    assertThat(resolved.version()).isEqualTo(4);
                    assertThat(resolved.prompt()).isEqualTo("关注长期偏好");
                })
                .verifyComplete();
    }

    @Test
    void settingsForAllLayersReturnsThreeEntries() {
        when(client.listAll(eq(AiMemoryPromptBinding.class), any(), any())).thenReturn(Flux.empty());
        when(client.listAll(eq(AiMemoryPrompt.class), any(), any())).thenReturn(Flux.empty());

        StepVerifier.create(service.settings(null, null))
                .assertNext(settings -> assertThat(settings).hasSize(3))
                .verifyComplete();
    }

    @Test
    void logRejectsInvalidLimit() {
        StepVerifier.create(service.logs(null, null, null, Long.MAX_VALUE, 500))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo(MemoryPromptSettings.CODE_INVALID))
                .verify();
    }

    private static AiMemoryPrompt prompt(String name, int version) {
        var spec = new AiMemoryPrompt.Spec();
        spec.setName(name);
        spec.setLayer(AiMemoryPrompt.Layer.L1);
        spec.setPrompt("关注长期偏好");
        spec.setVersion(version);
        spec.setStatus(AiMemoryPrompt.PromptStatus.ACTIVE);
        var prompt = new AiMemoryPrompt();
        prompt.setSpec(spec);
        var metadata = new Metadata();
        metadata.setName(name);
        prompt.setMetadata(metadata);
        return prompt;
    }

    private static AiTeam team(String name) {
        var team = new AiTeam();
        team.setSpec(new AiTeam.Spec());
        var metadata = new Metadata();
        metadata.setName(name);
        team.setMetadata(metadata);
        return team;
    }

    private static AiMemoryPromptBinding binding(String name, String promptName, AiMemoryPrompt.Layer layer) {
        var spec = new AiMemoryPromptBinding.Spec();
        spec.setTargetType(AiMemoryPromptBinding.TargetType.INSTANCE);
        spec.setLayer(layer);
        spec.setPrompt(promptName);
        var binding = new AiMemoryPromptBinding();
        binding.setSpec(spec);
        var metadata = new Metadata();
        metadata.setName(name);
        binding.setMetadata(metadata);
        return binding;
    }
}