package cn.tolim.pudans.core.agent.tool;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentToolCapabilitiesTest {

    // 工具目录必须与运行时注册的生成工具一致。
    @Test
    void exposesGenerateMediaAsAnEnabledTool() {
        var tools = new AgentToolCapabilities().tools().collectList().block();

        assertThat(tools).singleElement().satisfies(tool -> {
            assertThat(tool.key()).isEqualTo(GenerateMediaTool.NAME);
            assertThat(tool.enabled()).isTrue();
        });
    }
}
