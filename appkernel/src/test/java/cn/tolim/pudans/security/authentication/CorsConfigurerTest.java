package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.properties.PudansProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CorsConfigurerTest {

    @Test
    void apiCatchAllDoesNotAllowCredentials() {
        var configurer = new CorsConfigurer(new PudansProperties());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get(
                        "/apis/api.pudans.tolim.cn/v1alpha1/users")
                .build());

        var configuration = configurer.apiCorsConfigSource().getCorsConfiguration(exchange);

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowCredentials()).isFalse();
    }
}
