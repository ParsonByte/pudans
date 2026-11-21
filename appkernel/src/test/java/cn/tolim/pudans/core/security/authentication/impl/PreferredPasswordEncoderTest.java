package cn.tolim.pudans.core.security.authentication.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.infra.setting.SecurityParameters;
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
}
