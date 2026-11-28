package cn.tolim.pudans.communal.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class SensitiveDataMaskerTests {

    @Test
    void masksEmailLocalPart() {
        assertThat(SensitiveDataMasker.maskEmail("alice@example.com")).isEqualTo("a****@example.com");
        assertThat(SensitiveDataMasker.maskEmail("a@example.com")).isEqualTo("****@example.com");
    }

    @Test
    void masksPhoneKeepingPrefixAndSuffix() {
        assertThat(SensitiveDataMasker.maskPhone("13812345678")).isEqualTo("138****5678");
        assertThat(SensitiveDataMasker.maskPhone("138-1234-5678")).isEqualTo("138****5678");
        assertThat(SensitiveDataMasker.maskPhone("12345")).isEqualTo("****");
    }

    @Test
    void masksSecretsAndAuthorizationHeaders() {
        assertThat(SensitiveDataMasker.maskSecret("super-secret")).isEqualTo("****");
        assertThat(SensitiveDataMasker.maskAuthorization("Bearer abc.def.ghi")).isEqualTo("Bearer ****");
        assertThat(SensitiveDataMasker.maskAuthorization("opaque")).isEqualTo("****");
    }

    @Test
    void handlesNullAndBlankValues() {
        assertThat(SensitiveDataMasker.maskEmail(null)).isNull();
        assertThat(SensitiveDataMasker.maskPhone(" ")).isEqualTo(" ");
        assertThat(SensitiveDataMasker.maskSecret(null)).isNull();
    }

    @Test
    void rejectsNegativeVisibility() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SensitiveDataMasker.maskValue("value", -1, 0));
    }
}