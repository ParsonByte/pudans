package cn.tolim.pudans.core.ai.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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

    private AiMemoryContextBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new AiMemoryContextBuilder(recallService);
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
        when(recallService.build(CONTEXT, "发布流程", 5, 3))
                .thenReturn(Mono.just(new MemoryInjection("prepend", "append", List.of())));

        StepVerifier.create(builder.build(CONTEXT, "发布流程", 5, 3))
                .assertNext(injection -> {
                    assertThat(injection.prependContext()).isEqualTo("prepend");
                    assertThat(injection.appendSystemContext()).isEqualTo("append");
                })
                .verifyComplete();

        verify(recallService).build(CONTEXT, "发布流程", 5, 3);
    }

    @Test
    void emptyRecallResultFallsBackToEmptyInjection() {
        when(recallService.build(CONTEXT, "发布流程", 5, 3)).thenReturn(Mono.empty());

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
