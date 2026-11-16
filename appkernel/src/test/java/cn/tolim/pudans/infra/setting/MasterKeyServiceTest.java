package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.tolim.pudans.infra.properties.PudansProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MasterKeyServiceTest {

    @Test
    void signsAndVerifiesWithTheCurrentMasterKey(@TempDir Path workDir) {
        var service = service(workDir);
        service.initialize("key-1");

        var signature = service.hash("secret");

        assertThat(signature).hasSize(64);
        assertThat(service.matches(signature, "secret", 0)).isTrue();
        assertThat(service.matches(signature, "other", 0)).isFalse();
    }

    @Test
    void legacyTokensWorkOnlyBeforeTheFirstRotation(@TempDir Path workDir) {
        var service = service(workDir);
        service.initialize("key-1");
        var legacyHash = sha256("secret");

        assertThat(service.matches(legacyHash, "secret", null)).isTrue();

        service.initialize("key-2");

        assertThat(service.currentVersion()).isEqualTo(1);
        assertThat(service.matches(legacyHash, "secret", null)).isFalse();
        assertThat(service.matches(service.hash("secret"), "secret", 0)).isFalse();
        assertThat(service.matches(service.hash("secret"), "secret", 1)).isTrue();
    }

    @Test
    void restartingWithTheSameKeyKeepsTheVersion(@TempDir Path workDir) {
        var first = service(workDir);
        first.initialize("key-1");
        var second = service(workDir);
        second.initialize("key-1");

        assertThat(second.currentVersion()).isZero();
        assertThat(second.hash("secret")).isEqualTo(first.hash("secret"));
    }

    private static MasterKeyService service(Path workDir) {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        return new MasterKeyService(mock(ParameterSettingGroupService.class), properties);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
