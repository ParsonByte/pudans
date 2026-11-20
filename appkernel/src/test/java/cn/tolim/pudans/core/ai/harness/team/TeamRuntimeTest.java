package cn.tolim.pudans.core.ai.harness.team;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class TeamRuntimeTest {

    private static final String USER = "user-1";

    private final TeamRuntime runtime = new TeamRuntime("team-a", (team, userId) -> Mono.empty());

    @Test
    void movesTasksThroughBoardStatesAndHonoursBlockedBy() {
        runtime.addMember(USER, "lead", "lead-agent", true).block(Duration.ofSeconds(3));
        runtime.addMember(USER, "alice", "alice-agent", false).block(Duration.ofSeconds(3));
        runtime.addMember(USER, "bob", "bob-agent", false).block(Duration.ofSeconds(3));

        var first = runtime.createTask(USER, "first", "prepare", List.of(), "").block(Duration.ofSeconds(3));
        var blocked = runtime.createTask(
                        USER, "second", "consumes first", List.of(first.taskId()), "")
                .block(Duration.ofSeconds(3));

        var assigned = runtime.assignTask(USER, first.taskId(), "alice", first.version())
                .block(Duration.ofSeconds(3));
        assertThat(assigned.owner()).isEqualTo("alice");
        assertThat(assigned.state()).isEqualTo(TeamTaskState.PENDING);

        StepVerifier.create(runtime.claimTask(USER, "bob", blocked.taskId(), 0))
                .expectError(TeamConflictException.class)
                .verify();

        var claimedFirst = runtime.claimTask(USER, "alice", assigned.taskId(), assigned.version())
                .block(Duration.ofSeconds(3));
        assertThat(claimedFirst.state()).isEqualTo(TeamTaskState.IN_PROGRESS);
        assertThat(runtime.claimTask(USER, "alice", claimedFirst.taskId(), 0).block())
                .isSameAs(claimedFirst);

        var completed = runtime.completeTask(USER, first.taskId(), "prepared")
                .block(Duration.ofSeconds(3));
        assertThat(completed.state()).isEqualTo(TeamTaskState.COMPLETED);
        assertThat(completed.result()).isEqualTo("prepared");

        var claimedSecond = runtime.claimTask(USER, "bob", blocked.taskId(), 0)
                .block(Duration.ofSeconds(3));
        assertThat(claimedSecond.owner()).isEqualTo("bob");
        assertThat(claimedSecond.state()).isEqualTo(TeamTaskState.IN_PROGRESS);

        var failed = runtime.failTask(USER, blocked.taskId(), "cannot consume")
                .block(Duration.ofSeconds(3));
        assertThat(failed.state()).isEqualTo(TeamTaskState.FAILED);
        assertThat(failed.result()).isEqualTo("cannot consume");

        StepVerifier.create(runtime.failTask(USER, blocked.taskId(), "again"))
                .expectError(TeamConflictException.class)
                .verify();
        assertThat(runtime.inbox(USER, "lead", 50).block(Duration.ofSeconds(3)))
                .anyMatch(message -> message.content().contains("completed")
                        && message.content().contains("prepared"))
                .anyMatch(message -> message.content().contains("failed")
                        && message.content().contains("cannot consume"));
    }

    @Test
    void sendsDirectAndBroadcastMailboxMessagesAndManagesMembers() {
        runtime.addMember(USER, "lead", "lead-agent", true).block(Duration.ofSeconds(3));
        runtime.addMember(USER, "alice", "alice-agent", false).block(Duration.ofSeconds(3));
        runtime.addMember(USER, "bob", "bob-agent", false).block(Duration.ofSeconds(3));

        var direct = runtime.sendMessage(USER, "lead", "alice", "direct note")
                .block(Duration.ofSeconds(3));
        assertThat(direct.from()).isEqualTo("lead");
        assertThat(runtime.inbox(USER, "alice", 50).block(Duration.ofSeconds(3)))
                .extracting(TeamMessage::content)
                .containsExactly("direct note");

        StepVerifier.create(runtime.sendMessage(USER, "alice", "alice", "self"))
                .expectError(IllegalArgumentException.class)
                .verify();

        var broadcast = runtime.broadcastMessage(USER, "lead", "all hands")
                .block(Duration.ofSeconds(3));
        assertThat(broadcast).hasSize(2);
        assertThat(runtime.inbox(USER, "bob", 50).block(Duration.ofSeconds(3)))
                .extracting(TeamMessage::content)
                .containsExactly("all hands");

        runtime.sendMessage(USER, "alice", "lead", "second").block(Duration.ofSeconds(3));
        runtime.sendMessage(USER, "bob", "lead", "third").block(Duration.ofSeconds(3));
        assertThat(runtime.inbox(USER, "lead", 1).block(Duration.ofSeconds(3)))
                .extracting(TeamMessage::content)
                .containsExactly("third");

        runtime.removeMember(USER, "bob").block(Duration.ofSeconds(3));
        assertThat(runtime.members(USER).block(Duration.ofSeconds(3)))
                .extracting(TeamMember::name)
                .containsExactly("lead", "alice");
        StepVerifier.create(runtime.removeMember(USER, "bob"))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void deniesAccessThroughTheConfiguredPolicy() {
        var denied = new TeamRuntime(
                "team-a", (team, userId) -> Mono.error(new IllegalAccessException("denied")));

        StepVerifier.create(denied.tasks(USER))
                .expectError(IllegalAccessException.class)
                .verify();
    }
}