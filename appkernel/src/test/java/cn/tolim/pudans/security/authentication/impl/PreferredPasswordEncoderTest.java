package cn.tolim.pudans.security.authentication.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.setting.SecurityParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PreferredPasswordEncoderTest {

    @Mock
    SecurityParameters securitySettings;

    private PreferredPasswordEncoder encoder;

    @BeforeEach
    void setUp() {
        encoder = new PreferredPasswordEncoder(securitySettings);
    }

    @Test
    void encodesWithArgon2WhenEnabled() {
        when(securitySettings.isArgon2PasswordStorageEnabled()).thenReturn(true);

        var encoded = encoder.encode("secret");

        assertThat(encoded).startsWith("{" + PreferredPasswordEncoder.ARGON2_ENCODING_ID + "}");
        assertThat(encoder.matches("secret", encoded)).isTrue();
    }

    @Test
    void encodesWithBcryptWhenDisabled() {
        when(securitySettings.isArgon2PasswordStorageEnabled()).thenReturn(false);

        var encoded = encoder.encode("secret");

        assertThat(encoded).startsWith("{" + PreferredPasswordEncoder.BCRYPT_ENCODING_ID + "}");
        assertThat(encoder.matches("secret", encoded)).isTrue();
    }

    @Test
    @SuppressWarnings("deprecation")
    void verifiesExistingArgon2PasswordsAfterSwitchingToBcrypt() {
        var argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
        var encoded = "{" + PreferredPasswordEncoder.ARGON2_ENCODING_ID + "}" + argon2.encode("secret");

        assertThat(encoder.matches("secret", encoded)).isTrue();
    }

    @Test
    void verifiesExistingBcryptPasswordsAfterSwitchingToArgon2() {
        var encoded = "{" + PreferredPasswordEncoder.BCRYPT_ENCODING_ID + "}"
                + new BCryptPasswordEncoder().encode("secret");

        assertThat(encoder.matches("secret", encoded)).isTrue();
    }

    // 旧 BCrypt 哈希在启用 Argon2 后应触发升级。
    @Test
    void upgradesBcryptHashesWhenArgon2IsEnabled() {
        when(securitySettings.isArgon2PasswordStorageEnabled()).thenReturn(true);
        var bcrypt = "{" + PreferredPasswordEncoder.BCRYPT_ENCODING_ID + "}"
                + new BCryptPasswordEncoder().encode("secret");
        var argon2 = "{" + PreferredPasswordEncoder.ARGON2_ENCODING_ID + "}"
                + Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode("secret");

        assertThat(encoder.upgradeEncoding(bcrypt)).isTrue();
        assertThat(encoder.upgradeEncoding(argon2)).isFalse();
    }

    // 关闭 Argon2 时不要降级已有哈希。
    @Test
    void doesNotUpgradeWhenArgon2IsDisabled() {
        when(securitySettings.isArgon2PasswordStorageEnabled()).thenReturn(false);
        var bcrypt = "{" + PreferredPasswordEncoder.BCRYPT_ENCODING_ID + "}"
                + new BCryptPasswordEncoder().encode("secret");

        assertThat(encoder.upgradeEncoding(bcrypt)).isFalse();
    }
}
