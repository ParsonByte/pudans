package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LandingControllerTest {

    @Test
    void redirectsAuthenticatedLandingToUserCenter() {
        var response = new LandingController().landing().block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("/uc/");
    }
}
