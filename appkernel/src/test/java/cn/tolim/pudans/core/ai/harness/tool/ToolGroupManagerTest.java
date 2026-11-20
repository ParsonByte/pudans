package cn.tolim.pudans.core.ai.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ToolGroupManagerTest {

    @Test
    void createsGroupsAndRejectsDuplicateNames() {
        var manager = new ToolGroupManager();
        var group = manager.create("analysis", "分析工具", false, ToolGroupScope.META);

        assertThat(group.name()).isEqualTo("analysis");
        assertThat(group.active()).isFalse();
        assertThat(manager.groups()).containsExactly(group);
        assertThat(manager.metaGroupNames()).containsExactly("analysis");
        assertThatThrownBy(() -> manager.create("analysis", "重复", true, ToolGroupScope.META))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("analysis");
    }

    @Test
    void keepsUngroupedToolsActiveAndHidesToolsOfInactiveGroups() {
        var manager = new ToolGroupManager();
        manager.create("analysis", "分析工具", false, ToolGroupScope.META);
        manager.addTool("analysis", "chart");

        assertThat(manager.isActiveTool("chart")).isFalse();
        assertThat(manager.isActiveTool("echo")).isTrue();

        manager.setActive("analysis", true);

        assertThat(manager.isActiveTool("chart")).isTrue();
    }

    @Test
    void resetsOnlyMetaGroupsWithReplacementSemantics() {
        var manager = new ToolGroupManager();
        manager.create("analysis", "分析工具", true, ToolGroupScope.META);
        manager.create("admin", "管理工具", false, ToolGroupScope.META);
        manager.create("developer", "开发工具", true, ToolGroupScope.EXTERNAL);

        manager.resetActiveGroups(List.of("admin"));

        assertThat(manager.get("analysis").active()).isFalse();
        assertThat(manager.get("admin").active()).isTrue();
        assertThat(manager.get("developer").active()).isTrue();
    }

    @Test
    void rejectsUnknownAndExternalGroupsDuringReset() {
        var manager = new ToolGroupManager();
        manager.create("developer", "开发工具", true, ToolGroupScope.EXTERNAL);

        assertThatThrownBy(() -> manager.resetActiveGroups(List.of("missing")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
        assertThatThrownBy(() -> manager.resetActiveGroups(List.of("developer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("developer");
        assertThat(manager.get("developer").active()).isTrue();
    }

    @Test
    void rejectsAddingToolsToUnknownGroups() {
        var manager = new ToolGroupManager();

        assertThatThrownBy(() -> manager.addTool("missing", "chart"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void notesExposeOnlyMetaGroupsAndMarkEmptySections() {
        var manager = new ToolGroupManager();
        manager.create("analysis", "分析工具", true, ToolGroupScope.META);
        manager.create("admin", "管理工具", false, ToolGroupScope.META);
        manager.create("developer", "开发工具", false, ToolGroupScope.EXTERNAL);

        var notes = manager.notes();

        assertThat(notes).contains("当前激活的工具组").contains("- analysis：分析工具");
        assertThat(notes).contains("未激活的工具组").contains("- admin：管理工具");
        assertThat(notes).doesNotContain("developer");
    }
}