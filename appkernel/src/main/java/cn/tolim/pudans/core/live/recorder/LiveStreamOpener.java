package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STREAM_FAILED;

import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.contract.live.dto.LiveStreamProtocol;
import cn.tolim.pudans.contract.live.dto.LiveStreamUrl;
import cn.tolim.pudans.core.live.resolve.LiveStreamUrlValidator;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.springframework.stereotype.Component;

// 拉流器：选地址、消毒 URL 并按流复制参数打开 JavaCV grabber
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveStreamOpener {

    private static final String PROTOCOL_WHITELIST = "http,https,tcp,tls,crypto";

    private final LiveProperties properties;

    private final LiveStreamUrlValidator urlValidator;

    private final LiveHlsGuard hlsGuard;

    // 优先沿用当前地址，否则按 FLV、HLS 顺序挑选第一个通过的地址
    public String selectUrl(LiveRoomInfo info, String currentUrl) {
        if (info == null || info.streams() == null || info.streams().isEmpty()) {
            throw LIVE_STREAM_FAILED.exception();
        }
        if (currentUrl != null && info.streams().stream().anyMatch(stream -> currentUrl.equals(stream.url()))) {
            var sanitized = urlValidator.sanitize(currentUrl);
            if (sanitized.isPresent()) {
                return sanitized.get();
            }
        }
        var preferred = preferredStreams(info.streams());
        for (var stream : preferred) {
            var sanitized = urlValidator.sanitize(stream.url());
            if (sanitized.isPresent()) {
                return sanitized.get();
            }
        }
        throw LIVE_STREAM_FAILED.exception();
    }

    // 重新校验当前地址，供重连前防止 DNS 重绑定
    public boolean isAllowed(String url) {
        return urlValidator.sanitize(url).isPresent();
    }

    // 配置超时、禁止重定向并预检 HLS 后打开拉流器
    public FFmpegFrameGrabber open(String url) {
        hlsGuard.verify(url);
        var grabber = new FFmpegFrameGrabber(url);
        var timeout = String.valueOf(properties.getRecorder().getReadTimeout().toMillis() * 1000);
        grabber.setOption("rw_timeout", timeout);
        grabber.setOption("stimeout", timeout);
        grabber.setOption("protocol_whitelist", PROTOCOL_WHITELIST);
        // 禁止 HTTP 重定向，避免预检地址被跳转到内网目标
        grabber.setOption("follow_redirects", "0");
        grabber.setOption("reconnect", "0");
        grabber.setOption("reconnect_streamed", "0");
        try {
            grabber.start();
            log.info("直播录制拉流成功 [{}]", host(url));
            return grabber;
        } catch (Exception error) {
            releaseQuietly(grabber);
            log.warn("直播录制拉流失败 [{}]：{}", host(url), error.getMessage());
            throw LIVE_STREAM_FAILED.exception();
        }
    }

    // 仅用于日志的地址主机名，非法地址返回 unknown
    public static String host(String url) {
        try {
            var host = URI.create(url).getHost();
            return host == null ? "unknown" : host;
        } catch (IllegalArgumentException error) {
            return "unknown";
        }
    }

    // 按 FLV 优先、HLS 其次排序候选地址
    private static List<LiveStreamUrl> preferredStreams(List<LiveStreamUrl> streams) {
        return streams.stream()
                .sorted((left, right) -> Integer.compare(rank(left), rank(right)))
                .toList();
    }

    // 优先选择 FLV 协议，其余协议排在其后
    private static int rank(LiveStreamUrl stream) {
        return stream.protocol() == LiveStreamProtocol.FLV ? 0 : 1;
    }

    // 释放拉流器失败只告警
    private static void releaseQuietly(FFmpegFrameGrabber grabber) {
        try {
            grabber.release();
        } catch (Exception error) {
            log.warn("释放拉流器失败：{}", error.getMessage());
        }
    }
}
