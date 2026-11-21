package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.core.webhook.OutboundRequestValidator;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LiveStreamUrlValidator {

    private static final Set<String> ALLOWED_PORTS = Set.of("80", "443", "8080", "8443");

    private final Function<String, InetAddress[]> resolver;

    // 生产环境使用系统 DNS 解析，测试可注入固定结果。
    public LiveStreamUrlValidator() {
        this(LiveStreamUrlValidator::resolveAll);
    }

    // 允许测试注入固定 DNS 解析结果
    LiveStreamUrlValidator(Function<String, InetAddress[]> resolver) {
        this.resolver = resolver;
    }

    // 仅放行公网 HTTP(S) 地址并限制端口
    public Optional<String> sanitize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = URI.create(raw.trim());
        } catch (IllegalArgumentException error) {
            return Optional.empty();
        }
        var scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return Optional.empty();
        }
        if (uri.getUserInfo() != null || !StringUtils.hasText(uri.getHost())) {
            return Optional.empty();
        }
        var port = uri.getPort();
        if (port != -1 && !ALLOWED_PORTS.contains(String.valueOf(port))) {
            return Optional.empty();
        }
        if (!isPublicHost(uri.getHost())) {
            return Optional.empty();
        }
        return Optional.of(uri.toString());
    }

    // 复用统一的出站地址判断，并解析域名，避免通过公网域名指向内网。
    private boolean isPublicHost(String host) {
        var value = OutboundRequestValidator.stripBrackets(host).toLowerCase(Locale.ROOT);
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (OutboundRequestValidator.isForbiddenHost(value)) {
            return false;
        }
        if (OutboundRequestValidator.isIpLiteral(value)) {
            return !OutboundRequestValidator.isBlockedLiteral(value);
        }
        if (value.endsWith(".localhost")
                || value.endsWith(".local")
                || value.endsWith(".internal")
                || value.endsWith(".home.arpa")) {
            return false;
        }
        var addresses = resolver.apply(value);
        if (addresses == null || addresses.length == 0) {
            return false;
        }
        for (var address : addresses) {
            if (OutboundRequestValidator.isBlocked(address)) {
                return false;
            }
        }
        return true;
    }

    // 解析失败按不可用处理，宁可拒绝也不让拉流器自行决定目标。
    private static InetAddress[] resolveAll(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException error) {
            return new InetAddress[0];
        }
    }
}
