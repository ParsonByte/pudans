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
            throw new IllegalArgumentException("The webhook url must not be blank.");
        }
        try {
            return new URI(url.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("The webhook url is not a valid uri.", e);
        }
    }

    public void validate(String url) {
        var uri = parse(url);
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Only http and https webhook urls are allowed.");
        }
        var host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new IllegalArgumentException("The webhook url must contain a host.");
        }
        if (FORBIDDEN_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("The webhook target [" + host + "] is not allowed.");
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Unable to resolve the webhook host [" + host + "].", e);
        }
        for (var address : addresses) {
            if (isBlocked(address)) {
                throw new IllegalArgumentException(
                        "The webhook target [" + host + "] resolves to a non routable address.");
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
                log.warn("Dropping the forbidden webhook header [{}]", name);
                return;
            }
            sanitized.put(name, value);
        });
        return sanitized;
    }
}
