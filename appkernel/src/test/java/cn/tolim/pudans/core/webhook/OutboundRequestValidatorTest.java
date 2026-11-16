package cn.tolim.pudans.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OutboundRequestValidatorTest {

    private final OutboundRequestValidator validator = new OutboundRequestValidator();

    @Test
    void loopbackTargetIsRejected() {
        assertThatThrownBy(() -> validator.validate("http://127.0.0.1/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
    }

    @Test
    void cloudMetadataTargetIsRejected() {
        assertThatThrownBy(() -> validator.validate("http://169.254.169.254/latest/meta-data"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
    }

    @Test
    void privateNetworkTargetIsRejected() {
        assertThatThrownBy(() -> validator.validate("http://10.0.0.5/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
        assertThatThrownBy(() -> validator.validate("http://192.168.1.10/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
        assertThatThrownBy(() -> validator.validate("http://172.16.0.7/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
    }

    @Test
    void localhostNameIsRejectedBeforeResolution() {
        assertThatThrownBy(() -> validator.validate("http://localhost:8080/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许");
    }

    @Test
    void ipv6NonRoutableTargetsAreRejected() {
        assertThatThrownBy(() -> validator.validate("http://[::1]/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
        assertThatThrownBy(() -> validator.validate("http://[fc00::1]/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
        assertThatThrownBy(() -> validator.validate("http://[fe80::1]/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可路由");
    }

    @Test
    void cidrBoundariesAreClassified() throws Exception {
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("10.0.0.0")))
                .isTrue();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("10.255.255.255")))
                .isTrue();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("11.0.0.1")))
                .isFalse();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("172.16.0.1")))
                .isTrue();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("172.31.255.255")))
                .isTrue();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("172.32.0.1")))
                .isFalse();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("192.168.255.255")))
                .isTrue();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("fc00::1")))
                .isTrue();
        assertThat(OutboundRequestValidator.isBlocked(java.net.InetAddress.getByName("fdff:ffff::1")))
                .isTrue();
        assertThat(OutboundRequestValidator.isBlocked(
                        java.net.InetAddress.getByName("2001:4860:4860::8888")))
                .isFalse();
    }

    @Test
    void nonHttpSchemeIsRejected() {
        assertThatThrownBy(() -> validator.validate("ftp://example.com/hook"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP 和 HTTPS");
    }

    @Test
    void malformedUrlIsRejected() {
        assertThatThrownBy(() -> validator.validate("not a url"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sensitiveHeadersAreDroppedAndCustomHeadersAreKept() {
        var headers = new LinkedHashMap<String, String>();
        headers.put("Host", "evil.example.com");
        headers.put("Authorization", "Bearer secret");
        headers.put("Cookie", "session=1");
        headers.put("X-Pudans-Delivery", "forged");
        headers.put("X-Custom", "kept");

        Map<String, String> sanitized = validator.sanitizeHeaders(headers);

        assertThat(sanitized).containsOnlyKeys("X-Custom");
        assertThat(sanitized.get("X-Custom")).isEqualTo("kept");
    }
}
