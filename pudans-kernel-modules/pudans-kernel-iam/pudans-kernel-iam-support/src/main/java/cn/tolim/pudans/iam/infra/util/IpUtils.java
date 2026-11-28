package cn.tolim.pudans.iam.infra.util;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

public final class IpUtils {
    public static final String IPV4 = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
    public static final String IPV4_CIDR = IPV4.replace("$", "") + "/(?:[12]?[0-9]|3[0-2])$";
    public static final String IPV6 = "^[0-9a-fA-F:]+$";
    public static final String IPV6_CIDR = "^[0-9a-fA-F:]+/(?:[0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8])$";

    private IpUtils() {
    }

    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return "Unknown";
        }
        for (String header : new String[]{"X-Forwarded-For", "Proxy-Client-IP",
                "WL-Proxy-Client-IP", "HTTP_CLIENT_IP",
                "HTTP_X_FORWARDED_FOR", "X-Real-IP"}) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !"unknown".equalsIgnoreCase(value)) {
                return value.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    public static boolean isInternalIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (ip.startsWith("127.") || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return true;
        }
        if (!ip.matches(IPV4)) {
            return false;
        }
        String[] parts = ip.split("\\.");
        int first = Integer.parseInt(parts[0]);
        int second = Integer.parseInt(parts[1]);
        return first == 10 || first == 127 || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168) || (first == 169 && second == 254);
    }

    public static boolean isIpOrCidr(Set<String> values) {
        if (values == null) {
            return true;
        }
        for (String value : values) {
            if (!value.matches(IPV4) && !value.matches(IPV6) && !value.matches(IPV4_CIDR)
                    && !value.matches(IPV6_CIDR)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isInRange(String ip, String cidr) {
        if (cidr == null) {
            return false;
        }
        if (!cidr.contains("/")) {
            return ip.equals(cidr);
        }
        try {
            String[] parts = cidr.split("/", 2);
            byte[] address = InetAddress.getByName(ip).getAddress();
            byte[] network = InetAddress.getByName(parts[0]).getAddress();
            int prefix = Integer.parseInt(parts[1]);
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int index = 0; index < fullBytes; index++) {
                if (address[index] != network[index]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                return (address[fullBytes] & mask) == (network[fullBytes] & mask);
            }
            return true;
        } catch (UnknownHostException | NumberFormatException ignored) {
            return false;
        }
    }

    public static boolean isInRange(String ip, List<String> ranges) {
        return ranges != null && ranges.stream().anyMatch(range -> isInRange(ip, range));
    }

    public static String getHostIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ignored) {
            return "Unknown";
        }
    }

    public static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            return "Unknown";
        }
    }
}
