package cn.tolim.pudans.core.live.recorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import cn.tolim.pudans.core.live.resolve.LiveStreamUrlValidator;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;

class LiveHlsGuardTest {

    private final WebClient webClient = mock(WebClient.class);

    private final LiveStreamUrlValidator validator = mock(LiveStreamUrlValidator.class);

    private final LiveHlsGuard guard = new LiveHlsGuard(webClient, validator, new LiveProperties());

    @Test
    void rejectsInternalSegmentAddresses() {
        allowPublicUrls();

        assertThatThrownBy(() -> guard.verifyText(
                        "https://pull.example.com/live.m3u8",
                        "#EXTM3U\n#EXTINF:1,\nhttp://127.0.0.1/segment.ts\n",
                        0,
                        new HashSet<>()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LIVE_STREAM_FAILED"));
    }

    @Test
    void rejectsInternalTagAddresses() {
        allowPublicUrls();

        assertThatThrownBy(() -> guard.verifyText(
                        "https://pull.example.com/live.m3u8",
                        "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"http://127.0.0.1/key\"\n",
                        0,
                        new HashSet<>()))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo("LIVE_STREAM_FAILED"));
    }

    @Test
    void acceptsPublicSegmentsAndTagAddresses() {
        allowPublicUrls();

        assertThatCode(() -> guard.verifyText(
                        "https://pull.example.com/live.m3u8",
                        "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://cdn.example.com/key\"\n"
                                + "#EXTINF:1,\nsegment.ts\n",
                        0,
                        new HashSet<>()))
                .doesNotThrowAnyException();
    }

    @Test
    void skipsNonPlaylistUrls() {
        guard.verify("https://pull.example.com/live.flv");

        verifyNoInteractions(validator, webClient);
    }

    // 模拟统一校验器仅放行公网主机。
    private void allowPublicUrls() {
        when(validator.sanitize(anyString())).thenAnswer(invocation -> {
            var url = invocation.getArgument(0, String.class);
            return url.contains("127.0.0.1") ? Optional.<String>empty() : Optional.of(url);
        });
    }
}
