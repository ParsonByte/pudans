package cn.tolim.pudans.core.ai.memory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.generation.TextGenerations;
import cn.tolim.pudans.core.ai.generation.GenerationRequest;
import cn.tolim.pudans.core.ai.generation.GenerationResult;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2Pipeline.ExtractedScene;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SceneSegmentationTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    TextGenerations generationService;

    @Mock
    AiSettings settingService;

    private SceneSegmentation service;

    @BeforeEach
    void setUp() {
        service = new SceneSegmentation(
                new MemoryAiSupport(generationService, settingService, new ObjectMapper()));
    }

    @Test
    void filtersUnknownIdsAndSkipsScenesWithoutValidIds() {
        stubGeneration("""
                {"scenes":[
                  {"sceneKey":"release","title":"发布","summary":"发布汇总",
                   "l1Ids":[11,99,21,11],"confidence":0.9},
                  {"sceneKey":"empty","title":"空场景","summary":"无效引用",
                   "l1Ids":[99],"confidence":0.5},
                  {"sceneKey":"review","title":"评审","summary":"评审汇总",
                   "l1Ids":[12],"confidence":2.5}]}
                """);

        var scenes = service.extract(
                        "BASE_L2",
                        List.of(l1Record(11L), l1Record(12L)),
                        List.of(existingScene()))
                .block();

        assertThat(scenes).hasSize(2);
        assertThat(scenes).extracting(ExtractedScene::sceneKey).containsExactly("release", "review");
        assertThat(scenes.getFirst().l1Ids()).containsExactly(11L, 21L);
        assertThat(scenes.getFirst().confidence()).isEqualByComparingTo(new BigDecimal("0.9"));
        assertThat(scenes.get(1).l1Ids()).containsExactly(12L);
        assertThat(scenes.get(1).confidence()).isEqualByComparingTo(BigDecimal.ONE);

        var captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(generationService).generate(captor.capture());
        var request = captor.getValue();
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.maxOutputTokens()).isEqualTo(2048);
        assertThat(request.systemPrompt()).startsWith("BASE_L2").contains("JSON 结构");
        assertThat(request.userPrompt()).contains("内容-11").contains("release");
    }

    @Test
    void returnsNoScenesWithoutCallingTheModelWhenThereIsNoNewL1() {
        var scenes = service.extract("BASE_L2", List.of(), List.of()).block();

        assertThat(scenes).isEmpty();
        verifyNoInteractions(generationService, settingService);
    }

    @Test
    void rejectsMalformedJson() {
        stubGeneration("not-json");

        StepVerifier.create(service.extract("BASE_L2", List.of(l1Record(11L)), List.of()))
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

    private static L1MemoryRecord l1Record(long id) {
        return L1MemoryRecord.newRecord(
                        "m-" + id,
                        new MemoryContext("team-1", "user-1", "agent-1", "session-1", null),
                        9L,
                        "内容-" + id,
                        MemoryType.EPISODIC,
                        50,
                        "release",
                        List.of(id),
                        Map.of(),
                        "hash-" + id,
                        MemoryScope.PRIVATE,
                        false,
                        1,
                        List.of(NOW))
                .withId(id);
    }

    private static L2SceneBlock existingScene() {
        return new L2SceneBlock(
                5L,
                "team-1",
                "agent-1",
                "existing",
                "已有场景",
                "已有汇总",
                List.of(21L),
                1,
                new BigDecimal("0.8"),
                NOW);
    }
}
