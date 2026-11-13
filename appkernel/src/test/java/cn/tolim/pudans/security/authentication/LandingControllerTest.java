package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.properties.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LandingControllerTest {

    @Test
    void redirectsAuthenticatedLandingToTheConfiguredPostLoginPage() {
        var response = new LandingController(new SecurityProperties()).landing().block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation()).hasToString("/console");
    }
}
