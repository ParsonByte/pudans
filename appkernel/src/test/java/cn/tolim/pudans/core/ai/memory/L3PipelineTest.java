package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryGenerationLogRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class L3PipelineTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    @Mock
    AiMemoryL3Repository l3Repository;

    @Mock
    AiMemoryGenerationLogRepository generationLogRepository;

    private L3Pipeline service;

    @BeforeEach
    void setUp() {
        service = new L3Pipeline(
                new JevGate(), new PersonaTrigger(), new MemoryPromptResolver(), l3Repository, generationLogRepository);
        service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void doesNotGenerateWhenTriggerAndJevAreQuiet() {
        when(l3Repository.findByScope("team-1", "agent-1")).thenReturn(Mono.just(currentPersona()));

        var result = service.process(
                        request(state(false, 2, 1, 10), new MemoryPromptInput("BASE_L3", null)),
                        (persona, scene) -> JevChangeType.NONE,
                        (prompt, current, scenes) -> {
                            throw new AssertionError("不应调用 Persona 生成器");
                        })
                .block();

        assertThat(result).isNotNull();
        assertThat(result.triggerReason()).isEqualTo(PersonaTrigger.Reason.NONE);
        assertThat(result.jevDecision().decision()).isEqualTo(JevDecisionType.DEFERRED);
        assertThat(result.generated()).isFalse();
        assertThat(result.persona()).isNull();
        verify(l3Repository, never()).upsert(any());
        verifyNoInteractions(generationLogRepository);
    }

    @Test
    void personaTriggerBypassesJevAndGenerates() {
        var promptReference = new AtomicReference<String>();
        var resolved = new ResolvedMemoryPrompt(
                AiMemoryPrompt.Layer.L3, MemoryPromptSource.TEAM, "team-l3", 3, "关注团队画像规则");
        when(l3Repository.findByScope("team-1", "agent-1")).thenReturn(Mono.just(currentPersona()));
        when(l3Repository.upsert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L3Persona>getArgument(0).withId(44L)));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        var result = service.process(
                        request(state(true, 2, 1, 10), new MemoryPromptInput("BASE_L3", resolved)),
                        (persona, scene) -> {
                            throw new AssertionError("PersonaTrigger 命中时不应调用 JEV 分类器");
                        },
                        (prompt, current, scenes) -> {
                            promptReference.set(prompt);
                            return new L3Pipeline.GeneratedPersona(
                                    Map.of("background", "new"),
                                    "New persona",
                                    3,
                                    2,
                                    new BigDecimal("0.95"));
                        })
                .block();

        assertThat(result).isNotNull();
        assertThat(result.triggerReason()).isEqualTo(PersonaTrigger.Reason.EXPLICIT_REQUEST);
        assertThat(result.jevDecision()).isNull();
        assertThat(result.generated()).isTrue();
        assertThat(result.persona().id()).isEqualTo(44L);
        assertThat(result.persona().personaText()).isEqualTo("New persona");
        assertThat(result.persona().lastRefreshedAt()).isEqualTo(NOW);
        assertThat(promptReference.get()).startsWith("BASE_L3").contains("关注团队画像规则");
        var logCaptor = ArgumentCaptor.forClass(MemoryGenerationLog.class);
        verify(generationLogRepository).insert(logCaptor.capture());
        var log = logCaptor.getValue();
        assertThat(log.memoryLevel()).isEqualTo(AiMemoryPrompt.Layer.L3);
        assertThat(log.memoryId()).isEqualTo(44L);
        assertThat(log.promptId()).isEqualTo("team-l3");
        assertThat(log.promptVersion()).isEqualTo(3);
        assertThat(log.promptSource()).isEqualTo(MemoryPromptSource.TEAM);
        assertThat(log.promptHash()).isEqualTo(MemoryHashes.sha256Hex(promptReference.get()));
    }

    @Test
    void jevImmediateChangeGeneratesWithoutTrigger() {
        when(l3Repository.findByScope("team-1", "agent-1")).thenReturn(Mono.just(currentPersona()));
        when(l3Repository.upsert(any())).thenAnswer(invocation -> Mono.just(
                invocation.<L3Persona>getArgument(0).withId(45L)));
        when(generationLogRepository.insert(any())).thenReturn(Mono.empty());

        var result = service.process(
                        request(state(false, 2, 1, 10), new MemoryPromptInput("BASE_L3", null)),
                        (persona, scene) -> JevChangeType.CONFLICT,
                        (prompt, current, scenes) -> new L3Pipeline.GeneratedPersona(
                                Map.of("background", "changed"),
                                "Changed persona",
                                4,
                                2,
                                new BigDecimal("0.92")))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.triggerReason()).isEqualTo(PersonaTrigger.Reason.NONE);
        assertThat(result.jevDecision().decision()).isEqualTo(JevDecisionType.IMMEDIATE);
        assertThat(result.generated()).isTrue();
        assertThat(result.persona().id()).isEqualTo(45L);
    }

    @Test
    void jevDeferredDoesNotGenerate() {
        when(l3Repository.findByScope("team-1", "agent-1")).thenReturn(Mono.just(currentPersona()));

        var result = service.process(
                        request(state(false, 2, 1, 10), new MemoryPromptInput("BASE_L3", null)),
                        (persona, scene) -> JevChangeType.REINFORCEMENT,
                        (prompt, current, scenes) -> {
                            throw new AssertionError("不应调用 Persona 生成器");
                        })
                .block();

        assertThat(result).isNotNull();
        assertThat(result.jevDecision().decision()).isEqualTo(JevDecisionType.DEFERRED);
        assertThat(result.generated()).isFalse();
        verify(l3Repository, never()).upsert(any());
        verifyNoInteractions(generationLogRepository);
    }

    @Test
    void jevUnavailableDoesNotGenerate() {
        when(l3Repository.findByScope("team-1", "agent-1")).thenReturn(Mono.empty());

        var result = service.process(
                        request(state(false, 2, 1, 10), new MemoryPromptInput("BASE_L3", null)),
                        (persona, scene) -> JevChangeType.CONFLICT,
                        (prompt, current, scenes) -> {
                            throw new AssertionError("不应调用 Persona 生成器");
                        })
                .block();

        assertThat(result).isNotNull();
        assertThat(result.jevDecision().decision()).isEqualTo(JevDecisionType.UNAVAILABLE);
        assertThat(result.generated()).isFalse();
        assertThat(result.persona()).isNull();
        verify(l3Repository, never()).upsert(any());
        verifyNoInteractions(generationLogRepository);
    }

    private static L3Pipeline.Request request(PersonaTrigger.State state, MemoryPromptInput prompt) {
        return new L3Pipeline.Request(
                "team-1",
                "agent-1",
                state,
                List.of(new JevGate.ChangedScene("release", "summary", "content")),
                prompt,
                MemoryGenerationInput.empty());
    }

    private static PersonaTrigger.State state(
            boolean explicitRequested, int scenesProcessed, int memoriesSinceLastPersona, int threshold) {
        return new PersonaTrigger.State(
                explicitRequested, true, true, true, scenesProcessed, memoriesSinceLastPersona, threshold);
    }

    private static L3Persona currentPersona() {
        return new L3Persona(
                3L,
                "team-1",
                "agent-1",
                Map.of("background", "current"),
                "Current persona",
                5,
                2,
                new BigDecimal("0.90"),
                NOW.minusSeconds(3600));
    }
}
