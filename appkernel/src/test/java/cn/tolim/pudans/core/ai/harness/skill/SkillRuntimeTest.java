package cn.tolim.pudans.core.ai.harness.skill;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.skill.curator.InMemorySkillUsageStore;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceManager;
import cn.tolim.pudans.core.ai.harness.workspace.WorkspaceSpec;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillRuntimeTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsSkillMarkdownAndFallsBackToTheDirectoryName() throws IOException {
        var workspace = workspace(tempDir.resolve("workspace"));
        writeSkill(workspace.skillsRoot(), "release", "release", "Write release notes", "# Release\n");
        writeSkill(workspace.skillsRoot(), "plain", null, null, "# Plain\n");
        var repository = new FilesystemSkillRepository(workspace.skillsRoot());

        var release = repository.find("release").orElseThrow();
        assertThat(release.source()).isEqualTo("filesystem");
        assertThat(release.description()).isEqualTo("Write release notes");
        assertThat(release.markdown()).contains("name: release").contains("# Release");
        var plain = repository.find("plain").orElseThrow();
        assertThat(plain.name()).isEqualTo("plain");
        assertThat(plain.description()).isNull();
        assertThat(repository.list()).extracting(SkillEntry::name).containsExactlyInAnyOrder("release", "plain");
    }

    @Test
    void loadsResourcesLazilyAndRejectsEscapes() throws IOException {
        var workspace = workspace(tempDir.resolve("workspace"));
        var skillDir = workspace.skillsRoot().resolve("release");
        writeSkill(workspace.skillsRoot(), "release", "release", "Write release notes", "# Release\n");
        Files.createDirectories(skillDir.resolve("references"));
        Files.writeString(skillDir.resolve("references/guide.md"), "v1");
        var repository = new FilesystemSkillRepository(workspace.skillsRoot());
        var entry = repository.find("release").orElseThrow();

        assertThat(entry.resources().read("references/guide.md")).contains("v1");
        Files.writeString(skillDir.resolve("references/guide.md"), "v2");
        assertThat(entry.resources().read("references/guide.md")).contains("v2");
        assertThat(entry.resources().read("../SKILL.md")).isEmpty();
        assertThat(entry.resources().read(skillDir.resolve("SKILL.md").toAbsolutePath().toString()))
                .isEmpty();
        assertThat(entry.resources().list()).containsExactly("references/guide.md");
    }

    @Test
    void appliesRepositoryOrderAsPrecedence() throws IOException {
        var workspace = workspace(tempDir.resolve("workspace"));
        writeSkill(workspace.skillsRoot(), "shared", "shared", "from workspace", "# Workspace\n");
        writeSkill(workspace.skillsRoot(), "workspace-only", "workspace-only", null, "# Workspace only\n");
        var classpathRoot = tempDir.resolve("classpath");
        writeSkill(classpathRoot.resolve("skills"), "shared", "shared", "from classpath", "# Classpath\n");
        Files.createDirectories(classpathRoot.resolve("skills/shared/references"));
        Files.writeString(classpathRoot.resolve("skills/shared/references/ref.md"), "classpath reference");

        try (var loader = new URLClassLoader(new URL[] {classpathRoot.toUri().toURL()})) {
            var classpath = new ClasspathSkillRepository("skills", loader);
            var workspaceRepository = new WorkspaceSkillRepository(workspace);
            var usage = new InMemorySkillUsageStore();
            var runtime = new SkillRuntime(List.of(workspaceRepository, classpath), usage);

            var catalog = runtime.catalog();
            assertThat(catalog.size()).isEqualTo(2);
            assertThat(catalog.names()).containsExactlyInAnyOrder("shared", "workspace-only");
            var shared = catalog.find("shared").orElseThrow();
            assertThat(shared.source()).isEqualTo("workspace");
            assertThat(shared.description()).isEqualTo("from workspace");
            assertThat(runtime.load("shared").orElseThrow().description()).isEqualTo("from workspace");
            assertThat(usage.get("shared").orElseThrow().viewCount()).isEqualTo(1);

            var classpathEntry = classpath.find("shared").orElseThrow();
            assertThat(classpathEntry.filesRoot()).isNull();
            assertThat(classpathEntry.resources().read("references/ref.md")).contains("classpath reference");

            var reversed = new SkillRuntime(List.of(classpath, workspaceRepository));
            assertThat(reversed.load("shared").orElseThrow().description()).isEqualTo("from classpath");
        }
    }

    @Test
    void rendersAvailableSkillsPrompt() throws IOException {
        var workspace = workspace(tempDir.resolve("workspace"));
        writeSkill(workspace.skillsRoot(), "release", "release", "Write release notes", "# Release\n");
        var runtime = new SkillRuntime(List.of(new WorkspaceSkillRepository(workspace)));

        var prompt = runtime.prompt();

        assertThat(prompt)
                .contains("## Available Skills")
                .contains("<name>release</name>")
                .contains("<description>Write release notes</description>")
                .contains("<source>workspace</source>");
        assertThat(new SkillRuntime(List.of()).prompt()).isEmpty();
    }

    private static WorkspaceManager workspace(Path root) {
        var manager = new WorkspaceManager(WorkspaceSpec.defaults(root));
        manager.ensureDirectories();
        return manager;
    }

    private static void writeSkill(
            Path skillsRoot,
            String directory,
            @Nullable String name,
            @Nullable String description,
            String body)
            throws IOException {
        var skillDir = skillsRoot.resolve(directory);
        Files.createDirectories(skillDir);
        var content = new StringBuilder();
        if (name != null) {
            content.append("---\nname: ").append(name).append('\n');
            if (description != null) {
                content.append("description: ").append(description).append('\n');
            }
            content.append("---\n");
        }
        content.append(body);
        Files.writeString(skillDir.resolve("SKILL.md"), content.toString());
    }
}