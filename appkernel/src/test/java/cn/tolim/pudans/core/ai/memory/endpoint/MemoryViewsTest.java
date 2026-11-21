package cn.tolim.pudans.core.ai.memory.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiAgent;
import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.memory.MemoryRecall;
import cn.tolim.pudans.core.ai.memory.L1MemoryRecord;
import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.L3Persona;
import cn.tolim.pudans.core.ai.memory.MemoryContext;
import cn.tolim.pudans.core.ai.memory.MemoryInjection;
import cn.tolim.pudans.core.ai.memory.MemoryScope;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import cn.tolim.pudans.core.ai.team.Teams;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL2Repository;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL3Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryViewsTest {

    @Mock
    AiSettings aiSettings;

    @Mock
    Teams teams;

    @Mock
    MemoryRecall memoryRecall;

    @Mock
    AiMemoryL1Repository l1Repository;

    @Mock
    AiMemoryL2Repository l2Repository;

    @Mock
    AiMemoryL3Repository l3Repository;

    private MemoryViews service;

    @BeforeEach
    void setUp() {
        service = new MemoryViews(
                aiSettings, teams, memoryRecall, l1Repository, l2Repository, l3Repository);
    }

    @Test
    void listsMemoriesInTheResolvedScope() {
        stubScope();
        when(l1Repository.listByUser("default", "default", "user-1", null, Long.MAX_VALUE, 20))
                .thenReturn(Flux.just(record("user-1")));

        StepVerifier.create(service.list("user-1", null, null, null, Long.MAX_VALUE, 20))
                .assertNext(memories -> assertThat(memories).hasSize(1))
                .verifyComplete();
    }

    @Test
    void searchesWithTheResolvedScope() {
        stubScope();
        when(memoryRecall.build(any(MemoryContext.class), eq("发布"), eq(5), eq(0)))
                .thenReturn(Mono.just(new MemoryInjection(
                        "facts",
                        "",
                        List.of(new MemoryInjection.Ref(
                                1L, "L1:1", "先冻结再发布", MemoryType.WORK_METHOD, "发布", 0.8)))));

        StepVerifier.create(service.search("user-1", null, null, "发布", 5))
                .assertNext(refs -> assertThat(refs).hasSize(1))
                .verifyComplete();
    }

    @Test
    void listsScenesInTheResolvedScope() {
        stubScope();
        when(l2Repository.findByScope("default", "default")).thenReturn(Flux.just(scene()));

        StepVerifier.create(service.scenes("user-1", null, null))
                .assertNext(scene -> assertThat(scene.title()).isEqualTo("发布流程"))
                .verifyComplete();
    }

    @Test
    void returnsThePersona() {
        stubScope();
        when(l3Repository.findByScope("default", "default")).thenReturn(Mono.just(persona()));

        StepVerifier.create(service.profile("user-1", null, null))
                .assertNext(persona -> assertThat(persona.personaText()).isEqualTo("偏好先写测试"))
                .verifyComplete();
    }

    @Test
    void returnsOnlyTheOwnersMemoryDetail() {
        stubScope();
        when(l1Repository.findByIds(List.of(1L))).thenReturn(Flux.just(record("user-1")));

        StepVerifier.create(service.detail("user-1", "L1:1", null, null))
                .assertNext(memory -> assertThat(memory.recordId()).isEqualTo("L1:1"))
                .verifyComplete();
    }

    @Test
    void hidesAnotherUsersMemoryDetail() {
        stubScope();
        when(l1Repository.findByIds(List.of(1L))).thenReturn(Flux.just(record("user-2")));

        StepVerifier.create(service.detail("user-1", "1", null, null))
                .expectError(ApiException.class)
                .verify();
    }

    private void stubScope() {
        when(aiSettings.spec()).thenReturn(Mono.just(new AiSetting.Spec()));
        when(teams.requireAgent("default", "user-1")).thenReturn(Mono.just(agent()));
    }

    private static AiAgent agent() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("default");
        agent.setMetadata(metadata);
        agent.getSpec().setTeam("default");
        agent.getSpec().setOwnerUserId("system");
        return agent;
    }

    private static L1MemoryRecord record(String userId) {
        return L1MemoryRecord.newRecord(
                "L1:1",
                new MemoryContext("default", userId, "default", "session-1", null),
                1L,
                "先冻结再发布",
                MemoryType.WORK_METHOD,
                50,
                "发布",
                List.of(1L),
                java.util.Map.of(),
                "hash-1",
                MemoryScope.PRIVATE,
                false,
                1,
                List.of(Instant.now()))
                .withId(1L);
    }

    private static L2SceneBlock scene() {
        return new L2SceneBlock(
                1L,
                "default",
                "default",
                "release",
                "发布流程",
                "发布前需要冻结变更",
                List.of(1L),
                1,
                BigDecimal.ONE,
                Instant.now());
    }

    private static L3Persona persona() {
        return new L3Persona(
                1L,
                "default",
                "default",
                java.util.Map.of(),
                "偏好先写测试",
                1,
                1,
                BigDecimal.ONE,
                Instant.now());
    }
}
