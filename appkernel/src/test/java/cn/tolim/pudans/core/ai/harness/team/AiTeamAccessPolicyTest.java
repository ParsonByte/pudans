package cn.tolim.pudans.core.ai.harness.team;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiTeam;
import cn.tolim.pudans.core.ai.team.AiTeamService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class AiTeamAccessPolicyTest {

    @Test
    void delegatesMembershipChecksToAiTeamService() {
        var teamService = mock(AiTeamService.class);
        when(teamService.requireTeam("team-a", "user-1")).thenReturn(Mono.just(new AiTeam()));
        var policy = new AiTeamAccessPolicy(teamService);

        StepVerifier.create(policy.requireMember("team-a", "user-1"))
                .verifyComplete();

        verify(teamService).requireTeam("team-a", "user-1");
    }
}