package cn.tolim.pudans.security.authentication.twofactor.totp;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.properties.PudansProperties;
import com.j256.twofactorauth.TimeBasedOneTimePasswordUtil;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultTotpAuthServiceTest {

    @TempDir
    Path workDir;

    @Test
    void matchesTheHighestStepInsideTheAllowedWindow() throws Exception {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        properties.getSecurity().getTotp().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        var service = new DefaultTotpAuthService(properties);
        var secret = service.generateTotpSecret();
        var step = 1_000_000L;
        var code = TimeBasedOneTimePasswordUtil.generateNumber(secret, step * 30_000L, 30);

        assertThat(service.matchStep(secret, code, step * 30_000L)).isEqualTo(step);
        assertThat(service.matchStep(secret, code, (step + 2) * 30_000L)).isNull();
    }

    @Test
    void acceptsCodesWithinOneTimeStepAndRejectsDistantOnes() throws Exception {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        properties.getSecurity().getTotp().setKey(Base64.getEncoder().encodeToString(new byte[32]));
        var service = new DefaultTotpAuthService(properties);
        var secret = service.generateTotpSecret();
        var step = 1_000_000L;
        var code = TimeBasedOneTimePasswordUtil.generateNumber(secret, step * 30_000L, 30);

        assertThat(service.matchStep(secret, code, (step - 1) * 30_000L)).isEqualTo(step);
        assertThat(service.matchStep(secret, code, (step + 1) * 30_000L)).isEqualTo(step);
        assertThat(service.matchStep(secret, code, (step - 2) * 30_000L)).isNull();
        assertThat(service.matchStep(secret, code, (step + 2) * 30_000L)).isNull();
    }
}
