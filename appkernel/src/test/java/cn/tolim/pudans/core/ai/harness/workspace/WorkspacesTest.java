package cn.tolim.pudans.core.ai.harness.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.infra.properties.PudansProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspacesTest {

    @TempDir
    Path tempDir;

    @Test
    void isolatesWorkspacesByAgentAndUser() {
        var properties = new PudansProperties();
        properties.setWorkDir(tempDir);
        var service = new Workspaces(properties);

        var first = service.manager("agent/a", "user:1");
        var second = service.manager("agent/a", "user:2");
        var other = service.manager("agent/b", "user:1");

        assertThat(first.root())
                .isEqualTo(tempDir.resolve("agents/agent_a/users/user_1").toAbsolutePath());
        assertThat(first.root()).isNotEqualTo(second.root());
        assertThat(first.root()).isNotEqualTo(other.root());
    }
}