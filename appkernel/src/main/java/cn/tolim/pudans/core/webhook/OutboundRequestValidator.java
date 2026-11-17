package cn.tolim.pudans.core.webhook;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class OutboundRequestValidator {

    private static final Set<String> FORBIDDEN_HEADERS =
            Set.of("host", "authorization", "cookie", "set-cookie", "content-length", "transfer-encoding",
                    "connection", "upgrade", "proxy-authorization", "proxy-connection", "te", "trailer");

    private static final Set<String> FORBIDDEN_HOSTS =
            Set.of("localhost", "localhost.localdomain", "metadata", "metadata.google.internal");

    public static boolean isBlocked(InetAddress address) {
        if (address == null) {
            return true;
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        var bytes = address.getAddress();
        if (bytes.length == 4) {
            var first = Byte.toUnsignedInt(bytes[0]);
            var second = Byte.toUnsignedInt(bytes[1]);
            if (first == 0
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 198 && (second == 18 || second == 19))
                    || first >= 240) {
                return true;
            }
        }
        if (bytes.length == 16) {
            var first = bytes[0] & 0xFE;
            if (first == 0xFC) {
                return true;
            }
            if ((bytes[0] & 0xFF) == 0xFF) {
                return true;
            }
        }
        return false;
    }

    private static URI parse(String url) {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("Webhook URL 不能为空。");
        }
        try {
            return new URI(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Webhook URL 不是有效的 URI。", e);
        }
    }

    public void validate(String url) {
        var uri = parse(url);
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Webhook URL 仅允许使用 HTTP 和 HTTPS。");
        }
        var host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("Webhook URL 必须包含主机名。");
        }
        if (FORBIDDEN_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("不允许使用 Webhook 目标 [" + host + "]。");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析 Webhook 主机 [" + host + "]。", e);
        }
        for (var address : addresses) {
            if (isBlocked(address)) {
                throw new IllegalArgumentException(
                        "Webhook 目标 [" + host + "] 解析到不可路由的地址。");
            }
        }
    }

    public Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        var sanitized = new LinkedHashMap<String, String>();
        if (headers == null) {
            return sanitized;
        }
        headers.forEach((name, value) -> {
            if (!StringUtils.hasText(name) || value == null) {
                return;
            }
            var normalized = name.toLowerCase(Locale.ROOT);
            if (FORBIDDEN_HEADERS.contains(normalized) || normalized.startsWith("x-pudans-")) {
                log.warn("丢弃被禁止的 Webhook 请求头 [{}]", name);
                return;
            }
            sanitized.put(name, value);
        });
        return sanitized;
    }
}
