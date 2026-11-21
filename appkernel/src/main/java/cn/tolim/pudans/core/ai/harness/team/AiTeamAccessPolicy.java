package cn.tolim.pudans.core.ai.harness.team;

import cn.tolim.pudans.core.ai.team.Teams;
import java.util.Objects;
import reactor.core.publisher.Mono;

public final class AiTeamAccessPolicy implements TeamAccessPolicy {

    private final Teams teams;

    public AiTeamAccessPolicy(Teams teams) {
        this.teams = Objects.requireNonNull(teams, "团队服务不能为空");
    }

    @Override
    public Mono<Void> requireMember(String teamName, String userId) {
        return teams.requireTeam(teamName, userId).then();
    }
}