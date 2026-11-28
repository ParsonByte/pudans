package cn.tolim.pudans.communal.redis.key;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RedisKeysTests {

    private final RedisKeys keys = new RedisKeys("pudans");

    @Test
    void buildsNamespacedKeys() {
        assertThat(keys.key("iam", "session", "sid-1")).isEqualTo("pudans:iam:session:sid-1");
    }

    @Test
    void exposesDomainKeyConventions() {
        assertThat(keys.loginSession("sid-1")).isEqualTo("pudans:iam:session:sid-1");
        assertThat(keys.ssoTicket("order", "ticket-1")).isEqualTo("pudans:iam:ticket:order:ticket-1");
        assertThat(keys.sessionRegistry("user-1")).isEqualTo("pudans:iam:registry:user-1");
        assertThat(keys.appAccess("order", "user-1")).isEqualTo("pudans:iam:access:order:user-1");
        assertThat(keys.lock("daily-job")).isEqualTo("pudans:lock:daily-job");
        assertThat(keys.rateLimit("login", "127.0.0.1")).isEqualTo("pudans:rate:login:127.0.0.1");
    }

    @Test
    void rejectsPrefixWithSeparator() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RedisKeys("pudans:iam"));
    }

    @Test
    void rejectsBlankKeyParts() {
        assertThatIllegalArgumentException().isThrownBy(() -> keys.key("iam", " "));
        assertThatIllegalArgumentException().isThrownBy(() -> keys.key());
    }
}