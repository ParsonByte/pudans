package cn.tolim.pudans.security.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.web.server.MockWebSession;

class PudansServerRequestCacheTest {

    private static final String SAVED_REQUEST_ATTRIBUTE = "SPRING_SECURITY_SAVED_REQUEST";

    private final PudansServerRequestCache cache = new PudansServerRequestCache();

    private final MockWebSession session = new MockWebSession();

    // 校验重定向地址只保留应用内路径和查询参数。
    @Test
    void resolvesRedirectWithinApplication() {
        var request = MockServerHttpRequest.get("/login")
                .queryParam("redirect_uri", "/dashboard?tab=1")
                .build();

        assertThat(PudansServerRequestCache.uriInApplication(request, URI.create("/dashboard?tab=1")))
                .isEqualTo("/dashboard?tab=1");
    }

    // 应用内相对路径允许保存，登录后回到原页面。
    @Test
    void savesSafeApplicationPath() {
        var exchange = exchange("/dashboard?tab=1");

        cache.saveRequest(exchange).block();

        assertThat(savedRequest()).isEqualTo("/dashboard?tab=1");
    }

    // 绝对地址只保留路径和查询参数，不能把用户带到外部站点。
    @Test
    void keepsOnlyTheApplicationPathForAbsoluteUrls() {
        var exchange = exchange("https://pudans.example.com/dashboard");

        cache.saveRequest(exchange).block();

        assertThat(savedRequest()).isEqualTo("/dashboard");
    }

    // 协议相对地址会被浏览器当作外站，必须拒绝保存。
    @Test
    void rejectsProtocolRelativeRedirects() {
        var exchange = exchange("//evil.example.com");

        cache.saveRequest(exchange).block();

        assertThat(savedRequest()).isNull();
    }

    // 编码后的反斜杠可能被浏览器归一化为协议相对地址。
    @Test
    void rejectsEncodedBackslashRedirects() {
        var exchange = exchange("/%5Cevil.example.com");

        cache.saveRequest(exchange).block();

        assertThat(savedRequest()).isNull();
    }

    // 非法 URI 不应导致登录入口报错。
    @Test
    void ignoresMalformedRedirects() {
        var exchange = exchange("://");

        cache.saveRequest(exchange).block();

        assertThat(savedRequest()).isNull();
    }

    private MockServerWebExchange exchange(String redirectUri) {
        return MockServerWebExchange.builder(MockServerHttpRequest.get("/login")
                        .queryParam("redirect_uri", redirectUri)
                        .build())
                .session(session)
                .build();
    }

    private Object savedRequest() {
        return session.getAttributes().get(SAVED_REQUEST_ATTRIBUTE);
    }
}
