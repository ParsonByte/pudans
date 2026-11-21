package cn.tolim.pudans.core.live.resolve;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LiveStreamUrlValidatorTest {

    private final LiveStreamUrlValidator validator = new LiveStreamUrlValidator();

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
}
