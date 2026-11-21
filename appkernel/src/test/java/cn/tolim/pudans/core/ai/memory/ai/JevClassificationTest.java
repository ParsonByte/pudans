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
import cn.tolim.pudans.core.ai.memory.JevChangeType;
import cn.tolim.pudans.core.ai.memory.JevGate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class JevClassificationTest {

    @Mock
    TextGenerations generationService;

    @Mock
    AiSettings settingService;

    private JevClassification service;

    @BeforeEach
    void setUp() {
        service = new JevClassification(
                new MemoryAiSupport(generationService, settingService, new ObjectMapper()));
    }

    @Test
    void parsesTheChangeTypeAndStripsFences() {
        stubGeneration("""
                ```json
                {"changeType":"conflict"}
                ```
                """);

        StepVerifier.create(service.classify("当前画像", scene()))
                .assertNext(type -> assertThat(type).isEqualTo(JevChangeType.CONFLICT))
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationService).generate(captor.capture());
        var request = captor.getValue();
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.maxOutputTokens()).isEqualTo(1024);
        assertThat(request.systemPrompt()).contains("CONFLICT").contains("JSON 结构");
        assertThat(request.userPrompt()).contains("当前画像").contains("release");
    }

    @Test
    void returnsNoneForAnUnknownLabel() {
        stubGeneration("{\"changeType\":\"ADDITIVE\"}");

        StepVerifier.create(service.classify("当前画像", scene()))
                .assertNext(type -> assertThat(type).isEqualTo(JevChangeType.NONE))
                .verifyComplete();
    }

    @Test
    void returnsNoneWhenTheResponseIsNotJson() {
        stubGeneration("not-json");

        StepVerifier.create(service.classify("当前画像", scene()))
                .assertNext(type -> assertThat(type).isEqualTo(JevChangeType.NONE))
                .verifyComplete();
    }

    private void stubGeneration(String content) {
        when(settingService.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(generationService.generate(any())).thenReturn(Mono.just(
                new GenerationResult(content, "model-code", "provider-a", "account-a")));
    }

    private static JevGate.ChangedScene scene() {
        return new JevGate.ChangedScene("release", "发布调整", "每周五发布改为每周四发布");
    }
}
