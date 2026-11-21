package cn.tolim.pudans.core.ai.harness.team;

import cn.tolim.pudans.core.ai.team.Teams;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TeamRuntimeRegistry {

    private final ConcurrentMap<String, TeamRuntime> runtimes = new ConcurrentHashMap<>();

    private final Teams teamService;

    private final TeamTaskStore taskStore;

    public TeamRuntime runtime(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            throw new IllegalArgumentException("团队名称不能为空。");
        }
        var name = teamName.trim();
        return runtimes.computeIfAbsent(
                name, key -> new TeamRuntime(key, new AiTeamAccessPolicy(teamService), taskStore));
    }
}