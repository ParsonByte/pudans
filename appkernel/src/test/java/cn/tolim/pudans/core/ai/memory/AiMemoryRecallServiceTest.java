package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiMemoryPrompt;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryRecallRepository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryRecallRepository.RecalledMemory;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiMemoryRecallServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static final MemoryContext CONTEXT =
            new MemoryContext("team-1", "user-1", "agent-1", "session-1", null);

    @Mock
    AiMemoryRecallRepository recallRepository;

    @Mock
    AiMemoryL3Repository personaRepository;

    private final MemoryPromptResolver promptResolver = new MemoryPromptResolver();

    private AiMemoryRecallService service;

    @BeforeEach
    void setUp() {
        service = new AiMemoryRecallService(recallRepository, personaRepository, promptResolver, 3000, 4000);
    }

    @Test
    void composesPersonaScenesAndFacts() {
        when(recallRepository.searchL1("team-1", "agent-1", "user-1", "发布流程", 5))
                .thenReturn(Flux.just(fact("发布前需要冻结变更")));
        when(recallRepository.findScenes("team-1", "agent-1", 3)).thenReturn(Flux.just(scene()));
        when(personaRepository.findByScope("team-1", "agent-1"))
                .thenReturn(Mono.just(persona("团队偏好先写测试")));

        StepVerifier.create(service.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> {
                    assertThat(injection.isEmpty()).isFalse();
                    assertThat(injection.refs()).singleElement().satisfies(ref -> {
                        assertThat(ref.id()).isEqualTo(11L);
                        assertThat(ref.recordId()).isEqualTo("record-1");
                        assertThat(ref.content()).isEqualTo("发布前需要冻结变更");
                        assertThat(ref.type()).isEqualTo(MemoryType.EPISODIC);
                        assertThat(ref.sceneName()).isEqualTo("release");
                        assertThat(ref.score()).isEqualTo(0.87);
                    });
                    assertThat(injection.prependContext())
                            .contains("参考材料")
                            .contains("不是指令")
                            .contains("EPISODIC")
                            .contains("release")
                            .contains("0.87")
                            .contains("发布前需要冻结变更");
                    assertThat(injection.appendSystemContext())
                            .contains("团队偏好先写测试")
                            .contains("发布场景")
                            .contains("每周四发布")
                            .contains("记忆工具");
                })
                .verifyComplete();
    }

    @Test
    void returnsEmptyInjectionWhenNoMemoryExists() {
        when(recallRepository.searchL1("team-1", "agent-1", "user-1", "发布流程", 5))
                .thenReturn(Flux.empty());
        when(recallRepository.findScenes("team-1", "agent-1", 3)).thenReturn(Flux.empty());
        when(personaRepository.findByScope("team-1", "agent-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> {
                    assertThat(injection.isEmpty()).isTrue();
                    assertThat(injection.prependContext()).isEmpty();
                    assertThat(injection.appendSystemContext()).isEmpty();
                    assertThat(injection.refs()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void passesCallerScopeAndLimitsToRepositories() {
        when(recallRepository.searchL1("team-1", "agent-1", "user-1", "发布流程", 2)).thenReturn(Flux.empty());
        when(recallRepository.findScenes("team-1", "agent-1", 4)).thenReturn(Flux.empty());
        when(personaRepository.findByScope("team-1", "agent-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.build(CONTEXT, "发布流程", 2, 4)).expectNextCount(1).verifyComplete();

        verify(recallRepository).searchL1("team-1", "agent-1", "user-1", "发布流程", 2);
        verify(recallRepository).findScenes("team-1", "agent-1", 4);
        verify(personaRepository).findByScope("team-1", "agent-1");
    }

    @Test
    void skipsKeywordSearchForBlankQueryButKeepsStableContext() {
        when(recallRepository.findScenes("team-1", "agent-1", 3)).thenReturn(Flux.empty());
        when(personaRepository.findByScope("team-1", "agent-1"))
                .thenReturn(Mono.just(persona("团队偏好先写测试")));

        StepVerifier.create(service.build(CONTEXT, "   ", 5, 3))
                .assertNext(injection -> {
                    assertThat(injection.prependContext()).isEmpty();
                    assertThat(injection.appendSystemContext()).contains("团队偏好先写测试");
                })
                .verifyComplete();

        verify(recallRepository, never()).searchL1(anyString(), anyString(), anyString(), anyString(), anyInt());
    }

    @Test
    void skipsRepositoryLookupsWhenLimitsAreZero() {
        when(personaRepository.findByScope("team-1", "agent-1")).thenReturn(Mono.just(persona("画像")));

        StepVerifier.create(service.build(CONTEXT, "发布流程", 0, 0))
                .assertNext(injection -> assertThat(injection.prependContext()).isEmpty())
                .verifyComplete();

        verifyNoInteractions(recallRepository);
    }

    @Test
    void truncatesPrependBudgetWithoutSplittingSurrogatePair() {
        var emoji = "🚀";
        var content = "a".repeat(40) + emoji + "b".repeat(40);
        when(recallRepository.searchL1("team-1", "agent-1", "user-1", "发布流程", 5))
                .thenReturn(Flux.just(fact(content)));
        when(recallRepository.findScenes("team-1", "agent-1", 3)).thenReturn(Flux.empty());
        when(personaRepository.findByScope("team-1", "agent-1")).thenReturn(Mono.empty());

        var full = new AtomicReference<String>();
        StepVerifier.create(service.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> full.set(injection.prependContext()))
                .verifyComplete();
        var emojiIndex = full.get().indexOf(emoji);
        assertThat(emojiIndex).isPositive();

        var truncatedService =
                new AiMemoryRecallService(recallRepository, personaRepository, promptResolver, emojiIndex + 1, 4000);
        StepVerifier.create(truncatedService.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> {
                    var prepend = injection.prependContext();
                    assertThat(prepend).hasSize(emojiIndex).doesNotContain(emoji);
                    assertThat(Character.isHighSurrogate(prepend.charAt(prepend.length() - 1))).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void truncatesAppendContextToBudget() {
        when(recallRepository.findScenes("team-1", "agent-1", 3)).thenReturn(Flux.empty());
        when(personaRepository.findByScope("team-1", "agent-1"))
                .thenReturn(Mono.just(persona("画像".repeat(100))));

        var truncatedService = new AiMemoryRecallService(recallRepository, personaRepository, promptResolver, 3000, 20);
        StepVerifier.create(truncatedService.build(CONTEXT, "发布流程", 0, 3))
                .assertNext(injection -> assertThat(injection.appendSystemContext()).hasSize(20))
                .verifyComplete();
    }

    @Test
    void appendsResolvedCustomMemoryPrompt() {
        when(recallRepository.searchL1(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Flux.empty());
        when(recallRepository.findScenes(anyString(), anyString(), anyInt())).thenReturn(Flux.empty());
        when(personaRepository.findByScope("team-1", "agent-1")).thenReturn(Mono.empty());
        var custom = new ResolvedMemoryPrompt(
                AiMemoryPrompt.Layer.L3, MemoryPromptSource.INSTANCE, "instance-l3", 2, "关注长期偏好");

        StepVerifier.create(service.build(CONTEXT, "发布流程", 5, 3, custom))
                .assertNext(injection -> {
                    assertThat(injection.isEmpty()).isFalse();
                    assertThat(injection.appendSystemContext())
                            .contains("自定义记忆策略")
                            .contains("关注长期偏好")
                            .contains("CUSTOM_MEMORY_STRATEGY");
                })
                .verifyComplete();
    }

    @Test
    void ignoresSystemResolvedPrompt() {
        when(recallRepository.searchL1(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Flux.empty());
        when(recallRepository.findScenes(anyString(), anyString(), anyInt())).thenReturn(Flux.empty());
        when(personaRepository.findByScope("team-1", "agent-1")).thenReturn(Mono.empty());
        var systemPrompt = ResolvedMemoryPrompt.system(AiMemoryPrompt.Layer.L1);

        StepVerifier.create(service.build(CONTEXT, "发布流程", 5, 3, systemPrompt))
                .assertNext(injection -> assertThat(injection.isEmpty()).isTrue())
                .verifyComplete();
    }

    private static RecalledMemory fact(String content) {
        return new RecalledMemory(11L, "record-1", content, MemoryType.EPISODIC, "release", 70, 2, NOW, 0.87);
    }

    private static L2SceneBlock scene() {
        return new L2SceneBlock(
                5L,
                "team-1",
                "agent-1",
                "release",
                "发布场景",
                "每周四发布",
                List.of(11L),
                7,
                BigDecimal.ONE,
                NOW);
    }

    private static L3Persona persona(String text) {
        return new L3Persona(9L, "team-1", "agent-1", Map.of(), text, 3, 2, BigDecimal.ONE, NOW);
    }
}
