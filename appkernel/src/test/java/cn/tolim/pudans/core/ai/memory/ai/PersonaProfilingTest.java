package cn.tolim.pudans.core.ai.memory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.generation.TextGenerations;
import cn.tolim.pudans.core.ai.generation.GenerationRequest;
import cn.tolim.pudans.core.ai.generation.GenerationResult;
import cn.tolim.pudans.core.ai.memory.JevGate;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PersonaProfilingTest {

    @Mock
    TextGenerations generationService;

    @Mock
    AiSettings settingService;

    private PersonaProfiling service;

    @BeforeEach
    void setUp() {
        service = new PersonaProfiling(
                new MemoryAiSupport(generationService, settingService, new ObjectMapper()));
    }

    @Test
    void validatesCountsAgainstSuppliedScenesAndClampsConfidence() {
        stubGeneration("""
                {"personaJson":{"background":"团队背景"},
                 "personaText":"团队画像正文","sourceL1Count":-3,"sourceL2Count":9,"confidence":1.4}
                """);

        var persona = service.generate(
                        "BASE_L3",
                        null,
                        List.of(scene("release", "发布调整"), scene("review", "评审调整")))
                .block();

        assertThat(persona).isNotNull();
        assertThat(persona.personaText()).isEqualTo("团队画像正文");
        assertThat(persona.personaJson()).containsEntry("background", "团队背景");
        assertThat(persona.sourceL1Count()).isZero();
        assertThat(persona.sourceL2Count()).isEqualTo(2);
        assertThat(persona.confidence()).isEqualByComparingTo(BigDecimal.ONE);

        var captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationService).generate(captor.capture());
        var request = captor.getValue();
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.maxOutputTokens()).isEqualTo(2048);
        assertThat(request.systemPrompt()).startsWith("BASE_L3").contains("personaText");
        assertThat(request.userPrompt()).contains("release").contains("发布调整");
    }

    @Test
    void defaultsConfidenceToSevenTenthsWhenMissing() {
        stubGeneration("""
                {"personaJson":{},"personaText":"画像","sourceL1Count":1,"sourceL2Count":1}
                """);

        var persona = service.generate("BASE_L3", null, List.of(scene("release", "发布调整"))).block();

        assertThat(persona).isNotNull();
        assertThat(persona.confidence()).isEqualByComparingTo(new BigDecimal("0.7"));
        assertThat(persona.sourceL1Count()).isEqualTo(1);
        assertThat(persona.sourceL2Count()).isEqualTo(1);
    }

    @Test
    void rejectsPersonaWithoutText() {
        stubGeneration("""
                {"personaJson":{},"personaText":"   ","sourceL1Count":1,"sourceL2Count":1}
                """);

        StepVerifier.create(service.generate("BASE_L3", null, List.of(scene("release", "发布调整"))))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-memory-generation-invalid");
                })
                .verify();
    }

    private void stubGeneration(String content) {
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(generationService.generate(any())).thenReturn(Mono.just(
                new GenerationResult(content, "model-code", "provider-a", "account-a")));
    }

    private static JevGate.ChangedScene scene(String sceneKey, String summary) {
        return new JevGate.ChangedScene(sceneKey, summary, "场景内容-" + sceneKey);
    }
}
