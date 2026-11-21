package cn.tolim.pudans.infra.utils;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

@Slf4j
public class IpAddressUtils {

    public static final String UNKNOWN = "unknown";

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private static final String REAL_IP = "X-Real-IP";

    private static final int MAX_FORWARDED_HOPS = 32;

    private static volatile List<Cidr> trustedProxies = List.of();

    private IpAddressUtils() {}

    // 解析并替换受信代理网段列表。
    public static void configure(List<String> proxies) {
        var parsed = new ArrayList<Cidr>();
        if (proxies != null) {
            for (var proxy : proxies) {
                var cidr = Cidr.parse(proxy);
                if (cidr == null) {
                    log.warn("忽略无效的受信代理配置项 [{}]", proxy);
                } else {
                    parsed.add(cidr);
                }
            }
        }
        trustedProxies = List.copyOf(parsed);
    }

    // 仅信任受信代理的转发头，逐步追溯真实客户端。
    public static String getClientIp(ServerHttpRequest request) {
        var remoteAddress = request.getRemoteAddress();
        var remote = remoteAddress == null || remoteAddress.isUnresolved()
                ? null
                : remoteAddress.getAddress().getHostAddress();
        if (remote == null) {
            return UNKNOWN;
        }
        if (!isTrustedProxy(remote)) {
            return remote;
        }
        var chain = forwardedChain(request.getHeaders().getFirst(FORWARDED_FOR));
        if (chain.isEmpty()) {
            var realIp = normalize(request.getHeaders().getFirst(REAL_IP));
            return realIp == null ? remote : realIp;
        }
        for (var hop : chain) {
            if (!isTrustedProxy(hop)) {
                return hop;
            }
        }
        return chain.get(chain.size() - 1);
    }

    // 判断地址是否落在任一受信网段内。
    public static boolean isTrustedProxy(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        var address = parseLiteral(ip);
        if (address == null) {
            return false;
        }
        for (var cidr : trustedProxies) {
            if (cidr.contains(address)) {
                return true;
            }
        }
        return false;
    }

    // 从右向左取最近的非空地址并限制跳数。
    private static List<String> forwardedChain(String header) {
        var chain = new ArrayList<String>();
        if (!StringUtils.hasText(header)) {
            return chain;
        }
        var parts = header.split(",");
        // 从右向左解析，跳过而非终止于非法项，代理追加的真实客户端 IP 位于最右
        for (var i = parts.length - 1; i >= 0 && chain.size() < MAX_FORWARDED_HOPS; i--) {
            var ip = normalize(parts[i]);
            if (ip == null) {
                continue;
            }
            chain.add(ip);
        }
        return chain;
    }

    // 去除方括号并规范化为字面 IP 地址。
    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        var candidate = value.trim();
        if (candidate.startsWith("[") && candidate.endsWith("]")) {
            candidate = candidate.substring(1, candidate.length() - 1);
        }
        var address = parseLiteral(candidate);
        return address == null ? null : address.getHostAddress();
    }

    // 仅接受合法 IP 字面量，避免触发 DNS 解析。
    private static InetAddress parseLiteral(String candidate) {
        if (!StringUtils.hasText(candidate) || !com.google.common.net.InetAddresses.isInetAddress(candidate)) {
            return null;
        }
        return com.google.common.net.InetAddresses.forString(candidate);
    }

    private record Cidr(byte[] network, int prefixLength) {

        // 解析单个 IP 或 CIDR，非法输入返回 null。
        static Cidr parse(String value) {
            if (!StringUtils.hasText(value)) {
                return null;
            }
            var trimmed = value.trim();
            var addressPart = trimmed;
            var prefixPart = trimmed.contains("/") ? trimmed.substring(trimmed.indexOf('/') + 1) : null;
            if (prefixPart != null) {
                addressPart = trimmed.substring(0, trimmed.indexOf('/'));
            }
            var address = parseLiteral(addressPart);
            if (address == null) {
                return null;
            }
            var bytes = address.getAddress();
            var maxPrefix = bytes.length * 8;
            int prefixLength;
            if (prefixPart == null) {
                prefixLength = maxPrefix;
            } else {
                try {
                    prefixLength = Integer.parseInt(prefixPart);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            if (prefixLength < 0 || prefixLength > maxPrefix) {
                return null;
            }
            return new Cidr(bytes, prefixLength);
        }

        // 按前缀长度逐字节比较地址是否在网段内。
        boolean contains(InetAddress address) {
            var candidate = address.getAddress();
            if (candidate.length != network.length) {
                return false;
            }
            var fullBytes = prefixLength / 8;
            for (var i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            var remainingBits = prefixLength % 8;
            if (remainingBits == 0) {
                return true;
            }
            var mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
