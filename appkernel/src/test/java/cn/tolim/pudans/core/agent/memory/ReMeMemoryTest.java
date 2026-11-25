package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
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
class ReMeMemoryTest {

    @Mock
    ReMeMemoryClient client;

    private ReMeMemoryProperties properties;

    private ReMeMemory memory;

    @BeforeEach
    void setUp() {
        properties = new ReMeMemoryProperties();
        properties.setEnabled(true);
        properties.setApiBaseUrl("http://reme.internal:8002");
        properties.setWorkspacePrefix("pudans");
        properties.setTimeout(Duration.ofSeconds(20));
        properties.setTopK(5);
        properties.setMaxContextChars(120);
        memory = new ReMeMemory(properties, null, client);
    }

    @Test
    void isInertUntilTheServerIsExplicitlyConfigured() {
        properties.setEnabled(false);

        StepVerifier.create(memory.recall("alice", "workspace-a", "部署偏好"))
                .expectNext("")
                .verifyComplete();
        StepVerifier.create(memory.record("alice", "workspace-a", "问题", "答案"))
                .verifyComplete();

        verifyNoInteractions(client);
    }

    @Test
    void recallsTheAnswerInAnIsolatedWorkspace() {
        when(client.search(anyString(), eq("发布策略"), eq(5)))
                .thenReturn(Mono.just(new ReMeMemoryClient.Answer("生产环境使用蓝绿发布。", true, List.of())));

        StepVerifier.create(memory.recall("alice", "workspace-a", "发布策略"))
                .expectNext("生产环境使用蓝绿发布。")
                .verifyComplete();

        var workspace = ArgumentCaptor.forClass(String.class);
        verify(client).search(workspace.capture(), eq("发布策略"), eq(5));
        assertThat(workspace.getValue())
                .startsWith("pudans:")
                .isNotEqualTo("pudans:alice:workspace-a");
    }

    @Test
    void recordsOnlyTheRealUserAndAssistantMessages() {
        when(client.add(anyString(), any()))
                .thenReturn(Mono.just(new ReMeMemoryClient.Answer("", true, List.of())));

        StepVerifier.create(memory.record(
                        "alice", "workspace-a", "生产环境使用蓝绿发布。", "已记录发布约束。"))
                .verifyComplete();

        var workspace = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        var messages = ArgumentCaptor.forClass((Class<List<ReMeMemoryClient.Message>>) (Class<?>) List.class);
        verify(client).add(workspace.capture(), messages.capture());
        assertThat(workspace.getValue()).startsWith("pudans:");
        assertThat(messages.getValue())
                .extracting(message -> message.role() + ":" + message.content())
                .containsExactly(
                        "user:生产环境使用蓝绿发布。",
                        "assistant:已记录发布约束。");
    }

    @Test
    void degradesToNoRecallWhenTheServerFails() {
        when(client.search(anyString(), anyString(), anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("unreachable")));

        StepVerifier.create(memory.recall("alice", "workspace-a", "发布策略"))
                .expectNext("")
                .verifyComplete();
    }

    @Test
    void usesTheMemoryFragmentsWhenNoAggregateAnswerExists() {
        when(client.search(anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(new ReMeMemoryClient.Answer(
                        "",
                        true,
                        List.of("发布前必须完成快照。", "回滚窗口为十五分钟。"))));

        StepVerifier.create(memory.recall("alice", "workspace-a", "发布约束"))
                .expectNext("发布前必须完成快照。\n回滚窗口为十五分钟。")
                .verifyComplete();
    }
}