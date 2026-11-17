package cn.tolim.pudans.infra.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

class IpAddressUtilsTest {

    @BeforeEach
    void configureTrustedProxies() {
        IpAddressUtils.configure(List.of("127.0.0.1", "::1", "10.0.0.0/8"));
    }

    @AfterEach
    void resetTrustedProxies() {
        IpAddressUtils.configure(List.of());
    }

    private static MockServerHttpRequest request(String remote, String forwardedFor) {
        try {
            var builder = MockServerHttpRequest.get("/api/v1/users")
                    .remoteAddress(new InetSocketAddress(InetAddress.getByName(remote), 12345));
            if (forwardedFor != null) {
                builder.header("X-Forwarded-For", forwardedFor);
            }
            return builder.build();
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void forwardedHeaderIsIgnoredWhenThePeerIsNotATrustedProxy() {
        var request = request("8.8.8.8", "1.2.3.4");

        assertThat(IpAddressUtils.getClientIp(request)).isEqualTo("8.8.8.8");
    }

    @Test
    void rightmostNonProxyHopWinsOverClientSuppliedEntries() {
        var request = request("127.0.0.1", "9.9.9.9, 1.2.3.4");

        assertThat(IpAddressUtils.getClientIp(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void trustedHopsAreSkippedFromTheRight() {
        var request = request("127.0.0.1", "1.2.3.4, 10.0.0.5, 10.0.0.6");

        assertThat(IpAddressUtils.getClientIp(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void invalidForwardedEntriesAreNotTrustedAtAll() {
        var request = request("127.0.0.1", "evil.example.com");

        assertThat(IpAddressUtils.getClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void hexLikeForwardedEntryIsNotTreatedAsAnAddress() {
        var request = request("127.0.0.1", "deadbeef");

        assertThat(IpAddressUtils.getClientIp(request)).isEqualTo("127.0.0.1");
    }

    @Test
    void ipv6LoopbackCanBeATrustedProxy() {
        var request = request("::1", "1.2.3.4");

        assertThat(IpAddressUtils.getClientIp(request)).isEqualTo("1.2.3.4");
    }

    @Test
    void cidrConfiguredProxyIsRecognised() {
        assertThat(IpAddressUtils.isTrustedProxy("10.1.2.3")).isTrue();
        assertThat(IpAddressUtils.isTrustedProxy("11.1.2.3")).isFalse();
        assertThat(IpAddressUtils.isTrustedProxy("not-an-ip")).isFalse();
    }
}
