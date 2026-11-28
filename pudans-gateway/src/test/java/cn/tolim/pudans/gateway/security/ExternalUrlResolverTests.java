package cn.tolim.pudans.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalUrlResolverTests {

    @Test
    void buildsUrlFromHostHeaderForRelativeRequestUri() {
        var request = MockServerHttpRequest.get("/apps/order/orders/1?tab=open")
                .header(HttpHeaders.HOST, "home.example.com")
                .build();

        String url = new ExternalUrlResolver(true).resolve(request);

        assertThat(url).isEqualTo("http://home.example.com/apps/order/orders/1?tab=open");
    }

    @Test
    void fallsBackToLocalhostWhenNoAuthorityIsAvailable() {
        var request = MockServerHttpRequest.get("/apps/order").build();

        String url = new ExternalUrlResolver(true).resolve(request);

        assertThat(url).isEqualTo("http://localhost/apps/order");
    }

    @Test
    void forwardedHeadersOverrideRequestOrigin() {
        var request = MockServerHttpRequest.get("/login")
                .header(HttpHeaders.HOST, "internal:8080")
                .header(ExternalUrlResolver.FORWARDED_PROTO_HEADER, "https")
                .header(ExternalUrlResolver.FORWARDED_HOST_HEADER, "pudans.example.com")
                .build();

        String url = new ExternalUrlResolver(true).resolve(request);

        assertThat(url).isEqualTo("https://pudans.example.com/login");
    }

    @Test
    void forwardedPortIsAppliedWhenHostHasNoPort() {
        var request = MockServerHttpRequest.get("/apps/order")
                .header(ExternalUrlResolver.FORWARDED_PROTO_HEADER, "https")
                .header(ExternalUrlResolver.FORWARDED_HOST_HEADER, "pudans.example.com")
                .header(ExternalUrlResolver.FORWARDED_PORT_HEADER, "8443")
                .build();

        String url = new ExternalUrlResolver(true).resolve(request);

        assertThat(url).isEqualTo("https://pudans.example.com:8443/apps/order");
    }

    @Test
    void explicitPortInForwardedHostIsPreserved() {
        var request = MockServerHttpRequest.get("/apps/order")
                .header(ExternalUrlResolver.FORWARDED_HOST_HEADER, "pudans.example.com:9443")
                .header(ExternalUrlResolver.FORWARDED_PORT_HEADER, "8443")
                .build();

        String url = new ExternalUrlResolver(true).resolve(request);

        assertThat(url).isEqualTo("http://pudans.example.com:9443/apps/order");
    }

    @Test
    void forwardedHeadersAreIgnoredWhenTrustIsDisabled() {
        var request = MockServerHttpRequest.get("/apps/order")
                .header(HttpHeaders.HOST, "internal:8080")
                .header(ExternalUrlResolver.FORWARDED_PROTO_HEADER, "https")
                .header(ExternalUrlResolver.FORWARDED_HOST_HEADER, "pudans.example.com")
                .build();

        String url = new ExternalUrlResolver(false).resolve(request);

        assertThat(url).isEqualTo("http://internal:8080/apps/order");
    }

    @Test
    void firstValueOfCommaSeparatedForwardedHeadersWins() {
        var request = MockServerHttpRequest.get("/login")
                .header(ExternalUrlResolver.FORWARDED_PROTO_HEADER, "https, http")
                .header(ExternalUrlResolver.FORWARDED_HOST_HEADER, "pudans.example.com, internal")
                .build();

        String url = new ExternalUrlResolver(true).resolve(request);

        assertThat(url).isEqualTo("https://pudans.example.com/login");
    }

    @Test
    void headerInjectionCharactersAreDiscarded() {
        var request = MockServerHttpRequest.get("/apps/order")
                .header(ExternalUrlResolver.FORWARDED_HOST_HEADER, "evil.example.com\r\nLocation: https://attacker.example.com")
                .build();

        String url = new ExternalUrlResolver(true).resolve(request);

        assertThat(url).doesNotContain("\r").doesNotContain("\n")
                .isEqualTo("http://localhost/apps/order");
    }
}