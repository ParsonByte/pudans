package cn.tolim.pudans.core.ai.harness.team;

import cn.tolim.pudans.core.ai.team.AiTeamService;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class AiTeamAccessPolicy implements TeamAccessPolicy {

    private final AiTeamService teamService;

    public AiTeamAccessPolicy(AiTeamService teamService) {
        this.teamService = Objects.requireNonNull(teamService, "团队服务不能为空");
    }

    @Override
    public Mono<Void> requireMember(String teamName, String userId) {
        return teamService.requireTeam(teamName, userId).then();
    }
}