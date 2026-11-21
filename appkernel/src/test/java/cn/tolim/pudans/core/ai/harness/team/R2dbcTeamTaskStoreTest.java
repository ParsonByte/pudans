package cn.tolim.pudans.core.ai.harness.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.infra.r2dbc.ai.team.AiTeamTaskRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class R2dbcTeamTaskStoreTest {

    @Mock
    AiTeamTaskRepository repository;

    @Test
    void createsATaskWithExistingDependencies() {
        when(repository.countExisting("team-a", List.of("task-0"))).thenReturn(Mono.just(1L));
        when(repository.insert(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var store = new R2dbcTeamTaskStore(repository);

        StepVerifier.create(store.create("team-a", "发布", "执行发布", List.of("task-0"), "alice"))
                .assertNext(task -> {
                    assertThat(task.subject()).isEqualTo("发布");
                    assertThat(task.blockedBy()).containsExactly("task-0");
                    assertThat(task.state()).isEqualTo(TeamTaskState.PENDING);
                })
                .verifyComplete();
    }

    @Test
    void rejectsClaimWhenDependenciesAreStillOpen() {
        when(repository.find("team-a", "task-1")).thenReturn(Mono.just(task("task-1", 1L, List.of("task-0"))));
        when(repository.unfinishedDependencies("team-a", List.of("task-0"))).thenReturn(Mono.just(1L));
        var store = new R2dbcTeamTaskStore(repository);

        StepVerifier.create(store.claim("team-a", "task-1", "alice", 1L))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(TeamConflictException.class)
                        .hasMessageContaining("阻塞"))
                .verify();
    }

    @Test
    void updatesTheTaskWithTheCurrentVersion() {
        when(repository.find("team-a", "task-1")).thenReturn(Mono.just(task("task-1", 2L, List.of())));
        when(repository.update(
                        eq("team-a"),
                        eq("task-1"),
                        eq(2L),
                        eq(TeamTaskState.IN_PROGRESS),
                        eq("alice"),
                        eq("")))
                .thenReturn(Mono.just(new TeamTask(
                        "task-1", "team-a", "发布", "", TeamTaskState.IN_PROGRESS, "alice",
                        List.of(), "", 3, Instant.now(), Instant.now())));
        var store = new R2dbcTeamTaskStore(repository);

        StepVerifier.create(store.claim("team-a", "task-1", "alice", 2L))
                .assertNext(task -> assertThat(task.state()).isEqualTo(TeamTaskState.IN_PROGRESS))
                .verifyComplete();
    }

    private static TeamTask task(String id, long version, List<String> blockedBy) {
        var now = Instant.now();
        return new TeamTask(
                id, "team-a", "发布", "", TeamTaskState.PENDING, "", blockedBy, "", version, now, now);
    }
}