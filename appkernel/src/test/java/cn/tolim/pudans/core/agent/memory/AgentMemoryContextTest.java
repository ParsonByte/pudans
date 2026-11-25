package cn.tolim.pudans.core.agent.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.agent.dto.MemoryRetrievalResult;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrievedItem;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrieveRequest;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AgentMemoryContextTest {

    @Mock
    MemoryRetrieval retrieval;

    @Mock
    ReMeMemory reMe;

    private AgentMemoryContext context;

    @BeforeEach
    void setUp() {
        context = new AgentMemoryContext(retrieval, reMe);
    }

    @Test
    void combinesConfirmedProjectMemoryWithReMeRecall() {
        when(retrieval.retrieve(
                        eq(new RequestIdentity("alice", "workspace-a")),
                        eq(new MemoryRetrieveRequest(
                                "发布约束", List.of("L1", "L2", "L3"), 8, 2_000, false))))
                .thenReturn(Mono.just(new MemoryRetrievalResult(
                        List.of(new MemoryRetrievedItem(
                                UUID.randomUUID(),
                                "personal",
                                "L2",
                                "preference",
                                "审批后使用蓝绿发布。",
                                "发布偏好",
                                BigDecimal.ONE,
                                Instant.parse("2026-11-01T00:00:00Z"),
                                null,
                                0.9,
                                List.of())),
                        12,
                        false,
                        List.of(),
                        List.of())));
        when(reMe.recall("alice", "workspace-a", "发布约束"))
                .thenReturn(Mono.just("历史回滚窗口为十五分钟。"));

        StepVerifier.create(context.recall("alice", "workspace-a", "发布约束"))
                .assertNext(result -> assertThat(result)
                        .contains("审批后使用蓝绿发布。")
                        .contains("历史回滚窗口为十五分钟。"))
                .verifyComplete();
    }

    @Test
    void keepsReMeRecallWhenProjectMemoryRetrievalFails() {
        when(retrieval.retrieve(any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("database unavailable")));
        when(reMe.recall("alice", "workspace-a", "发布约束"))
                .thenReturn(Mono.just("历史回滚窗口为十五分钟。"));

        StepVerifier.create(context.recall("alice", "workspace-a", "发布约束"))
                .assertNext(result -> assertThat(result)
                        .contains("历史回滚窗口为十五分钟。"))
                .verifyComplete();
    }

    @Test
    void recordsTheCompletedConversationInReMe() {
        when(reMe.record("alice", "workspace-a", "发布约束是什么？", "十五分钟内可回滚。"))
                .thenReturn(Mono.empty());

        StepVerifier.create(context.record(
                        "alice", "workspace-a", "发布约束是什么？", "十五分钟内可回滚。"))
                .verifyComplete();

        verify(reMe).record(
                "alice", "workspace-a", "发布约束是什么？", "十五分钟内可回滚。");
    }
}
