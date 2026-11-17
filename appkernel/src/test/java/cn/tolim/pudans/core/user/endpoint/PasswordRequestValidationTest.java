package cn.tolim.pudans.core.user.endpoint;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;

class PasswordRequestValidationTest {

    @Test
    void allPasswordDtosRequireTheSameMinimumLength() {
        var userPassword = UserEndpoint.PasswordRequest.class
                .getRecordComponents()[0]
                .getAccessor()
                .getAnnotation(Size.class);
        var ucPassword = UcUserEndpoint.ChangeMyPasswordRequest.class
                .getRecordComponents()[1]
                .getAccessor()
                .getAnnotation(Size.class);

        assertThat(userPassword.min()).isEqualTo(8);
        assertThat(ucPassword.min()).isEqualTo(8);
    }
}
