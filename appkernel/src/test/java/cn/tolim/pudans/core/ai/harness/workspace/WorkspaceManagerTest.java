package cn.tolim.pudans.core.ai.harness.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void readsAndWritesInsideTheWorkspace() {
        var manager = workspace(tempDir.resolve("workspace"));

        manager.writeText("notes/run.md", "hello");

        assertThat(manager.readText("notes/run.md")).contains("hello");
        assertThat(manager.resolve("notes/run.md")).startsWith(manager.root());
        assertThat(manager.skillsRoot()).isEqualTo(manager.root().resolve("skills"));
    }

    @Test
    void rejectsTraversalAndAbsolutePaths() {
        var manager = workspace(tempDir.resolve("workspace"));

        assertThatThrownBy(() -> manager.resolve("../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.resolve("nested/../../outside.txt"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.resolve(tempDir.resolve("outside.txt").toString()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> manager.writeText("../outside.txt", "blocked"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listsMovesDeletesAndRestoresSnapshots() {
        var manager = workspace(tempDir.resolve("workspace"));
        manager.writeText("a.txt", "one");
        manager.writeText("dir/b.md", "two");

        assertThat(manager.list(".")).extracting(WorkspaceManager.WorkspaceEntry::name)
                .containsExactly("a.txt", "dir", "skills");
        assertThat(manager.list("dir")).singleElement().satisfies(entry -> {
            assertThat(entry.name()).isEqualTo("b.md");
            assertThat(entry.directory()).isFalse();
        });

        manager.move("a.txt", "dir/a.txt");
        assertThat(manager.exists("a.txt")).isFalse();
        assertThat(manager.readText("dir/a.txt")).contains("one");

        var snapshot = manager.snapshot();
        assertThat(manager.snapshots()).contains(snapshot);
        manager.writeText("dir/a.txt", "changed");
        manager.delete("dir/a.txt");
        manager.restore(snapshot);

        assertThat(manager.readText("dir/a.txt")).contains("one");
        assertThat(manager.readText("dir/b.md")).contains("two");
        manager.delete("dir");
        assertThat(manager.exists("dir")).isFalse();
        assertThatThrownBy(() -> manager.delete("."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSymlinksThatEscapeTheWorkspace() throws IOException {
        var manager = workspace(tempDir.resolve("workspace"));
        var outside = tempDir.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(manager.root().resolve("escape"), outside);

        assertThatThrownBy(() -> manager.readText("escape/secret.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("工作区");
    }

    private static WorkspaceManager workspace(Path root) {
        var manager = new WorkspaceManager(WorkspaceSpec.defaults(root));
        manager.ensureDirectories();
        return manager;
    }
}