package cn.tolim.pudans.core.ai.harness.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.ai.team.AiTeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamRuntimeRegistryTest {

    @Mock
    AiTeamService teamService;

    @Test
    void reusesOneRuntimePerTeam() {
        var registry = new TeamRuntimeRegistry(teamService);

        var first = registry.runtime("team-a");
        var second = registry.runtime("team-a");
        var other = registry.runtime("team-b");

        assertThat(first).isSameAs(second);
        assertThat(first).isNotSameAs(other);
    }

    @Test
    void rejectsBlankTeamNames() {
        var registry = new TeamRuntimeRegistry(teamService);

        assertThatThrownBy(() -> registry.runtime(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}