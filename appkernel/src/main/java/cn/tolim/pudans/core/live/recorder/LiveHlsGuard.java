package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STREAM_FAILED;

import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import cn.tolim.pudans.core.live.resolve.LiveStreamUrlValidator;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import reactor.core.publisher.Mono;

// HLS 播放列表预检：校验主列表、子列表与标签内的所有目标地址。
@Slf4j
@Component
public class LiveHlsGuard {

    private static final Pattern TAG_URI = Pattern.compile("URI=\"([^\"]+)\"");

    private static final int MAX_DEPTH = 2;

    private static final int MAX_PLAYLISTS = 16;

    private static final int MAX_PLAYLIST_CHARS = 2 * 1024 * 1024;

    private final WebClient liveWebClient;

    private final LiveStreamUrlValidator urlValidator;

    private final LiveProperties properties;

    // 注入直播解析客户端、地址校验器与超时配置。
    public LiveHlsGuard(
            @Qualifier("liveWebClient") WebClient liveWebClient,
            LiveStreamUrlValidator urlValidator,
            LiveProperties properties) {
        this.liveWebClient = liveWebClient;
        this.urlValidator = urlValidator;
        this.properties = properties;
    }

    // 对 HLS 地址先抓取并校验全部引用，非 HLS 地址直接跳过。
    public void verify(String url) {
        if (!isPlaylist(url)) {
            return;
        }
        verifyPlaylist(url, 0, new HashSet<>());
    }

    // 递归校验主列表与子列表中的媒体、密钥和初始化分片地址。
    private void verifyPlaylist(String url, int depth, Set<String> visited) {
        if (depth > MAX_DEPTH || visited.size() >= MAX_PLAYLISTS || !visited.add(url)) {
            throw failed();
        }
        verifyText(url, fetch(url), depth, visited);
    }

    // 解析播放列表文本并校验全部引用地址。
    void verifyText(String url, String text, int depth, Set<String> visited) {
        var master = text.contains("#EXT-X-STREAM-INF") || text.contains("#EXT-X-MEDIA");
        for (var rawLine : text.split("\\R")) {
            var line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#")) {
                var matcher = TAG_URI.matcher(line);
                while (matcher.find()) {
                    var target = checked(url, matcher.group(1));
                    if (isPlaylist(target)) {
                        verifyPlaylist(target, depth + 1, visited);
                    }
                }
                continue;
            }
            var target = checked(url, line);
            if (master && isPlaylist(target)) {
                verifyPlaylist(target, depth + 1, visited);
            }
        }
    }

    // 读取播放列表并限制大小与超时。
    private String fetch(String url) {
        try {
            var text = liveWebClient
                    .get()
                    .uri(url)
                    .accept(MediaType.ALL)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> Mono.error(failed()))
                    .bodyToMono(String.class)
                    .block(timeout());
            if (text == null || text.length() > MAX_PLAYLIST_CHARS) {
                throw failed();
            }
            return text;
        } catch (ApiException error) {
            throw error;
        } catch (RuntimeException error) {
            log.warn("HLS 播放列表预检失败 [{}]：{}", host(url), error.getMessage());
            throw failed();
        }
    }

    // 解析相对地址并通过统一校验器确认目标公网可达。
    private String checked(String base, String value) {
        try {
            var target = URI.create(base).resolve(value.trim()).toString();
            return urlValidator.sanitize(target).orElseThrow(LiveHlsGuard::failed);
        } catch (IllegalArgumentException error) {
            throw failed();
        }
    }

    // 判断地址是否为 m3u8 播放列表。
    private static boolean isPlaylist(String url) {
        try {
            var path = URI.create(url).getPath();
            return path != null && path.toLowerCase(Locale.ROOT).endsWith(".m3u8");
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    // 在解析超时基础上增加一秒余量。
    private Duration timeout() {
        return properties.getResolve().getResponseTimeout().plusSeconds(1);
    }

    // 仅用于日志的主机名。
    private static String host(String url) {
        try {
            var value = URI.create(url).getHost();
            return StringUtils.hasText(value) ? value : "unknown";
        } catch (IllegalArgumentException error) {
            return "unknown";
        }
    }

    // 构造统一的拉流失败错误。
    private static ApiException failed() {
        return LIVE_STREAM_FAILED.exception();
    }
}
