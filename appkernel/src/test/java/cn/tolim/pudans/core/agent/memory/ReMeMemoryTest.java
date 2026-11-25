package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.agentscope.core.memory.reme.ReMeAddRequest;
import io.agentscope.core.memory.reme.ReMeClient;
import io.agentscope.core.memory.reme.ReMeSearchRequest;
import io.agentscope.core.memory.reme.ReMeSearchResponse;
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
    ReMeClient client;

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
        memory = new ReMeMemory(properties, client);
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
        var response = new ReMeSearchResponse();
        response.setSuccess(true);
        response.setAnswer("生产环境使用蓝绿发布。");

        when(client.search(any())).thenReturn(Mono.just(response));

        StepVerifier.create(memory.recall("alice", "workspace-a", "发布策略"))
                .expectNext("生产环境使用蓝绿发布。")
                .verifyComplete();

        var request = ArgumentCaptor.forClass(ReMeSearchRequest.class);
        verify(client).search(request.capture());
        assertThat(request.getValue().getWorkspaceId())
                .startsWith("pudans:")
                .isNotEqualTo("pudans:alice:workspace-a");
        assertThat(request.getValue().getQuery()).isEqualTo("发布策略");
        assertThat(request.getValue().getTopK()).isEqualTo(5);
    }

    @Test
    void recordsOnlyTheRealUserAndAssistantMessages() {
        when(client.add(any()))
                .thenReturn(Mono.just(new io.agentscope.core.memory.reme.ReMeAddResponse()));

        StepVerifier.create(memory.record(
                        "alice", "workspace-a", "生产环境使用蓝绿发布。", "已记录发布约束。"))
                .verifyComplete();

        var request = ArgumentCaptor.forClass(ReMeAddRequest.class);
        verify(client).add(request.capture());
        assertThat(request.getValue().getWorkspaceId()).startsWith("pudans:");
        assertThat(request.getValue().getTrajectories()).hasSize(1);
        assertThat(request.getValue().getTrajectories().getFirst().getMessages())
                .extracting(message -> message.getRole() + ":" + message.getContent())
                .containsExactly(
                        "user:生产环境使用蓝绿发布。",
                        "assistant:已记录发布约束。");
    }

    @Test
    void degradesToNoRecallWhenTheServerFails() {
        when(client.search(any())).thenReturn(Mono.error(new IllegalStateException("unreachable")));

        StepVerifier.create(memory.recall("alice", "workspace-a", "发布策略"))
                .expectNext("")
                .verifyComplete();
    }

    @Test
    void usesTheMemoryFragmentsWhenNoAggregateAnswerExists() {
        var first = new ReMeSearchResponse.MemoryItem();
        first.setContent("发布前必须完成快照。");
        var second = new ReMeSearchResponse.MemoryItem();
        second.setContent("回滚窗口为十五分钟。");
        var metadata = new ReMeSearchResponse.Metadata();
        metadata.setMemoryList(List.of(first, second));
        var response = new ReMeSearchResponse();
        response.setSuccess(true);
        response.setMetadata(metadata);
        when(client.search(any())).thenReturn(Mono.just(response));

        StepVerifier.create(memory.recall("alice", "workspace-a", "发布约束"))
                .expectNext("发布前必须完成快照。\n回滚窗口为十五分钟。")
                .verifyComplete();
    }
}
