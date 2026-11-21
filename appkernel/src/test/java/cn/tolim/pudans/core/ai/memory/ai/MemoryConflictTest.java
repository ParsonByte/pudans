package cn.tolim.pudans.core.ai.memory.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.generation.TextGenerations;
import cn.tolim.pudans.core.ai.generation.GenerationRequest;
import cn.tolim.pudans.core.ai.generation.GenerationResult;
import cn.tolim.pudans.core.ai.memory.DedupAction;
import cn.tolim.pudans.core.ai.memory.DedupDecision;
import cn.tolim.pudans.core.ai.memory.ExtractedMemory;
import cn.tolim.pudans.core.ai.memory.L1Deduplication.DedupCandidate;
import cn.tolim.pudans.core.ai.memory.L1Deduplication.ExistingMemory;
import cn.tolim.pudans.core.ai.memory.MemoryStatus;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class MemoryConflictTest {

    @Mock
    TextGenerations textGenerations;

    @Mock
    AiSettings aiSettings;

    private MemoryConflict service;

    @BeforeEach
    void setUp() {
        service = new MemoryConflict(
                new MemoryAiSupport(textGenerations, aiSettings, new ObjectMapper()));
    }

    @Test
    void mapsDecisionsAndDefaultsMissingOnesToStore() {
        stubGeneration("""
                {"decisions":[
                  {"recordId":"m1","action":"UPDATE","targetIds":["old-1"],
                   "mergedContent":"合并内容","mergedType":"WORK_FACT","mergedPriority":80,
                   "mergedTimestamps":["2026-11-19T00:00:00Z"]},
                  {"recordId":"m2","action":"UPDATE"},
                  {"recordId":"unknown","action":"SKIP"}]}
                """);

        var decisions = service.judge("BASE_L1", List.of(
                        candidate("m1", "内容一", "old-1"),
                        candidate("m2", "内容二", "old-2"),
                        candidate("m3", "内容三")))
                .block();

        assertThat(decisions).hasSize(3);
        var first = decisions.getFirst();
        assertThat(first.recordId()).isEqualTo("m1");
        assertThat(first.action()).isEqualTo(DedupAction.UPDATE);
        assertThat(first.targetIds()).containsExactly("old-1");
        assertThat(first.mergedContent()).isEqualTo("合并内容");
        assertThat(first.mergedType()).isEqualTo(MemoryType.WORK_FACT);
        assertThat(first.mergedPriority()).isEqualTo(80);
        assertThat(first.mergedTimestamps()).containsExactly("2026-11-19T00:00:00Z");
        assertThat(decisions.get(1).action()).isEqualTo(DedupAction.STORE);
        assertThat(decisions.get(2).action()).isEqualTo(DedupAction.STORE);

        var captor = ArgumentCaptor.forClass(GenerationRequest.class);
        verify(textGenerations).generate(captor.capture());
        var request = captor.getValue();
        assertThat(request.routeGroupCode()).isNull();
        assertThat(request.temperature()).isEqualTo(0.1);
        assertThat(request.maxOutputTokens()).isEqualTo(2048);
        assertThat(request.systemPrompt()).startsWith("BASE_L1").contains("decisions");
        assertThat(request.userPrompt()).contains("内容一").contains("old-1");
    }

    @Test
    void fallsBackToStoreForAllCandidatesWhenJsonIsMalformed() {
        stubGeneration("not-json");

        var decisions = service.judge("BASE_L1", List.of(
                        candidate("m1", "内容一"),
                        candidate("m2", "内容二")))
                .block();

        assertThat(decisions)
                .extracting(DedupDecision::recordId, DedupDecision::action)
                .containsExactly(tuple("m1", DedupAction.STORE), tuple("m2", DedupAction.STORE));
    }

    @Test
    void fallsBackToStoreForAllCandidatesWhenGenerationFails() {
        when(aiSettings.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(textGenerations.generate(any())).thenReturn(Mono.error(new IllegalStateException("boom")));

        var decisions = service.judge("BASE_L1", List.of(candidate("m1", "内容一"))).block();

        assertThat(decisions)
                .extracting(DedupDecision::recordId, DedupDecision::action)
                .containsExactly(tuple("m1", DedupAction.STORE));
    }

    private void stubGeneration(String content) {
        when(aiSettings.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(textGenerations.generate(any())).thenReturn(Mono.just(
                new GenerationResult(content, "model-code", "provider-a", "account-a")));
    }

    private static DedupCandidate candidate(String recordId, String content, String... oldIds) {
        var memory = new ExtractedMemory(
                recordId, content, MemoryType.EPISODIC, 50, "release", List.of("1"), Map.of());
        var existing = new ArrayList<ExistingMemory>();
        for (var oldId : oldIds) {
            existing.add(new ExistingMemory(oldId, "旧-" + oldId, "hash-" + oldId, MemoryStatus.ACTIVE));
        }
        return new DedupCandidate(memory, existing);
    }
}
