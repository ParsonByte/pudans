package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

class CsrfConfigurerTest {

    // Actuator 的写操作必须经过 CSRF 校验。
    @Test
    void protectsActuatorWrites() {
        assertThat(matches(MockServerHttpRequest.post("/actuator/restart"))).isTrue();
    }

    // 登录表单必须携带 CSRF 令牌，防止登录 CSRF。
    @Test
    void protectsLogin() {
        assertThat(matches(MockServerHttpRequest.post("/login"))).isTrue();
    }

    // 登出与 sudo 写操作同样必须携带 CSRF 令牌。
    @Test
    void protectsLogoutAndSudo() {
        assertThat(matches(MockServerHttpRequest.post("/logout"))).isTrue();
        assertThat(matches(MockServerHttpRequest.post("/sudo/confirm"))).isTrue();
    }

    // 携带 Bearer 令牌的请求不使用 Cookie 会话，因此无需 CSRF 令牌。
    @Test
    void skipsBearerTokenRequests() {
        assertThat(matches(MockServerHttpRequest.post("/apis/api.pudans.tolim.cn/v1alpha1/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")))
                .isFalse();
    }

    // 安全方法本身不改变状态，不需要 CSRF 防护。
    @Test
    void skipsSafeMethods() {
        assertThat(matches(MockServerHttpRequest.get("/actuator/health"))).isFalse();
    }

    private static boolean matches(MockServerHttpRequest.BaseBuilder<?> request) {
        var result = CsrfConfigurer.csrfMatcher()
                .matches(MockServerWebExchange.from(request.build()))
                .block();
        return result != null && result.isMatch();
    }
}
