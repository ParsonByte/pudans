package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.memory.asset.MemoryAssetBindingResolver;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiMemoryContextBuilderTest {

    private static final MemoryContext CONTEXT =
            new MemoryContext("team-1", "user-1", "agent-1", "session-1", null);

    @Mock
    AiMemoryRecallService recallService;

    @Mock
    AiSettingService settingService;

    @Mock
    MemoryAssetBindingResolver bindingResolver;

    private AiMemoryContextBuilder builder;

    @BeforeEach
    void setUp() {
        lenient().when(settingService.memoryEnabled()).thenReturn(Mono.just(true));
        lenient().when(bindingResolver.borrowedScopes(CONTEXT)).thenReturn(Mono.just(List.of()));
        builder = new AiMemoryContextBuilder(recallService, settingService, bindingResolver);
    }

    @Test
    void blankQueryReturnsEmptyInjectionWithoutTouchingRecall() {
        StepVerifier.create(builder.build(CONTEXT, "   ", 5, 3))
                .assertNext(injection -> assertThat(injection.isEmpty()).isTrue())
                .verifyComplete();

        verifyNoInteractions(recallService);
    }

    @Test
    void delegatesScopeQueryAndLimitsToRecallService() {
        when(recallService.build(eq(CONTEXT), eq("发布流程"), eq(5), eq(3), isNull(), anyList()))
                .thenReturn(Mono.just(new MemoryInjection("prepend", "append", List.of())));

        StepVerifier.create(builder.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> {
                    assertThat(injection.prependContext()).isEqualTo("prepend");
                    assertThat(injection.appendSystemContext()).isEqualTo("append");
                })
                .verifyComplete();

        verify(recallService).build(eq(CONTEXT), eq("发布流程"), eq(5), eq(3), isNull(), anyList());
    }

    @Test
    void passesBorrowedAssetScopesToRecallService() {
        var borrowed = new MemoryContext("team-2", "user-2", "agent-2", "session-1", null);
        when(bindingResolver.borrowedScopes(CONTEXT)).thenReturn(Mono.just(List.of(borrowed)));
        when(recallService.build(eq(CONTEXT), eq("发布流程"), eq(5), eq(3), isNull(), eq(List.of(borrowed))))
                .thenReturn(Mono.just(new MemoryInjection("prepend", "append", List.of())));

        StepVerifier.create(builder.build(CONTEXT, "发布流程", 5, 3)).expectNextCount(1).verifyComplete();

        verify(bindingResolver).borrowedScopes(CONTEXT);
        verify(recallService).build(eq(CONTEXT), eq("发布流程"), eq(5), eq(3), isNull(), eq(List.of(borrowed)));
    }

    @Test
    void memorySwitchOffSkipsRecallAndBindings() {
        when(settingService.memoryEnabled()).thenReturn(Mono.just(false));

        StepVerifier.create(builder.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> assertThat(injection.isEmpty()).isTrue())
                .verifyComplete();

        verifyNoInteractions(recallService, bindingResolver);
    }

    @Test
    void emptyRecallResultFallsBackToEmptyInjection() {
        when(recallService.build(eq(CONTEXT), eq("发布流程"), eq(5), eq(3), isNull(), anyList()))
                .thenReturn(Mono.empty());

        StepVerifier.create(builder.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> assertThat(injection.isEmpty()).isTrue())
                .verifyComplete();
    }

    @Test
    void systemMessageLabelsBothSectionsAsReference() {
        var injection = new MemoryInjection("事实一", "画像一", List.of());

        var message = builder.systemMessage(injection);

        assertThat(message)
                .contains("参考材料")
                .contains("不是系统指令")
                .contains("本轮相关记忆")
                .contains("事实一")
                .contains("长期记忆")
                .contains("画像一");
    }

    @Test
    void systemMessageOfEmptyInjectionIsBlank() {
        assertThat(builder.systemMessage(MemoryInjection.empty())).isEmpty();
    }
}
