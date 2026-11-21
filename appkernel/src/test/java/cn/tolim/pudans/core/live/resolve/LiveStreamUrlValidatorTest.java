package cn.tolim.pudans.core.live.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class LiveStreamUrlValidatorTest {

    // 用固定解析结果代替真实 DNS，保证测试不依赖网络。
    private final LiveStreamUrlValidator validator = new LiveStreamUrlValidator(host -> {
        try {
            if (host.endsWith(".nip.io")) {
                return new InetAddress[] {InetAddress.getByAddress(new byte[] {127, 0, 0, 1})};
            }
            return new InetAddress[] {InetAddress.getByAddress(
                    new byte[] {(byte) 93, (byte) 184, (byte) 216, (byte) 34})};
        } catch (UnknownHostException error) {
            throw new IllegalStateException(error);
        }
    });

    @Test
    void acceptsPublicHttpStreams() {
        assertThat(validator.sanitize("https://pull.example.com/live.m3u8")).contains("https://pull.example.com/live.m3u8");
        assertThat(validator.sanitize("http://pull.example.com:8080/live.flv")).contains("http://pull.example.com:8080/live.flv");
    }

    @Test
    void rejectsPrivateAndUnsupportedStreams() {
        assertThat(validator.sanitize("http://127.0.0.1/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://192.168.1.10/live.flv")).isEmpty();
        assertThat(validator.sanitize("file:///etc/passwd")).isEmpty();
        assertThat(validator.sanitize("https://pull.example.com:9000/live.flv")).isEmpty();
    }

    // 覆盖 IPv4 变形、IPv6 映射和特殊网段，避免流地址被当作内网探测入口。
    @Test
    void rejectsDisguisedPrivateAddresses() {
        assertThat(validator.sanitize("http://2130706433/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://0x7f000001/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://0177.0.0.1/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://[::ffff:127.0.0.1]/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://100.64.0.1/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://198.18.0.1/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://metadata.google.internal/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://camera.internal/live.flv")).isEmpty();
        assertThat(validator.sanitize("http://localhost./live.flv")).isEmpty();
        assertThat(validator.sanitize("http://127.0.0.1.nip.io/live.flv")).isEmpty();
    }
}
