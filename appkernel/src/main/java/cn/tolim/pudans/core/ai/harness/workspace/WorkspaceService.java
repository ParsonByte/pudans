package cn.tolim.pudans.core.ai.harness.workspace;

import cn.tolim.pudans.infra.properties.PudansProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private static final String AGENTS_DIRECTORY = "agents";

    private static final String USERS_DIRECTORY = "users";

    private final PudansProperties properties;

    public WorkspaceManager manager(String agentName, String userId) {
        if (!StringUtils.hasText(agentName) || !StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("工作区需要 Agent 与用户标识。");
        }
        var root = properties
                .getWorkDir()
                .toAbsolutePath()
                .normalize()
                .resolve(AGENTS_DIRECTORY)
                .resolve(safe(agentName))
                .resolve(USERS_DIRECTORY)
                .resolve(safe(userId));
        var manager = new WorkspaceManager(WorkspaceSpec.defaults(root));
        manager.ensureDirectories();
        return manager;
    }

    private static String safe(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}