package cn.tolim.pudans.core.ai.harness.skill;

import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceManager;
import java.nio.file.Path;

public final class WorkspaceSkillRepository extends FilesystemSkillRepository {

    public static final String SOURCE = "workspace";

    public static final String DRAFTS_DIRECTORY = "skills/_drafts";

    private static final String DRAFT_SOURCE = "workspace-draft";

    public WorkspaceSkillRepository(WorkspaceManager workspace) {
        this(workspace, workspace.spec().skillsDirectory(), SOURCE);
    }

    private WorkspaceSkillRepository(WorkspaceManager workspace, String directory, String source) {
        super(directory(workspace, directory), source, true);
    }

    public static WorkspaceSkillRepository drafts(WorkspaceManager workspace) {
        return new WorkspaceSkillRepository(workspace, DRAFTS_DIRECTORY, DRAFT_SOURCE);
    }

    private static Path directory(WorkspaceManager workspace, String directory) {
        if (workspace == null) {
            throw new IllegalArgumentException("工作区管理器不能为空。");
        }
        return workspace.resolve(directory);
    }
}