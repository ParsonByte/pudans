package cn.tolim.pudans.core.live.resolve;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LiveStreamUrlValidator {

    private static final Set<String> ALLOWED_PORTS = Set.of("80", "443", "8080", "8443");

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

    private static boolean isPublicHost(String host) {
        var value = host.toLowerCase();
        if ("localhost".equals(value) || value.endsWith(".localhost") || value.endsWith(".local")) {
            return false;
        }
        if (value.startsWith("10.") || value.startsWith("127.") || value.startsWith("0.")) {
            return false;
        }
        if (value.startsWith("192.168.") || value.startsWith("169.254.")) {
            return false;
        }
        if (value.startsWith("172.")) {
            var parts = value.split("\\.");
            if (parts.length > 1) {
                try {
                    var second = Integer.parseInt(parts[1]);
                    return second < 16 || second > 31;
                } catch (NumberFormatException ignored) {
                    return true;
                }
            }
        }
        if (value.equals("::1") || value.startsWith("fe80:") || value.startsWith("fc") || value.startsWith("fd")) {
            return false;
        }
        return true;
    }
}
