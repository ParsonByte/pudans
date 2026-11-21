package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.infra.properties.PudansProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParameterSettingTempPathResolverTest {

    @TempDir
    Path workDir;

    private ParameterSettingTempPathResolver resolver;

    @BeforeEach
    void setUp() {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        resolver = new ParameterSettingTempPathResolver(mock(ParameterGroups.class), properties);
    }

    @Test
    void workDirectoryRootIsNotAValidTempPath() {
        assertThat(resolver.resolve(".")).isEqualTo(workDir.resolve("tmp"));
    }

    @Test
    void keyDirectoryIsNotAValidTempPath() {
        assertThat(resolver.resolve("keys")).isEqualTo(workDir.resolve("tmp"));
    }

    @Test
    void dedicatedTempDirectoryIsAccepted() {
        assertThat(resolver.resolve("tmp")).isEqualTo(workDir.resolve("tmp"));
    }
}
