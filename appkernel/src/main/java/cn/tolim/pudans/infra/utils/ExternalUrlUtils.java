package cn.tolim.pudans.infra.utils;

import java.net.IDN;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;


public final class ExternalUrlUtils {

    private ExternalUrlUtils() {}


    // 将 URL 主机部分转为 ASCII 兼容形式。
    @Nullable
    public static String toAscii(@Nullable String externalUrl) {
        if (externalUrl == null || !externalUrl.contains("://")) {
            return externalUrl;
        }
        var schemeSeparatorIndex = externalUrl.indexOf("://");
        var authorityStartIndex = schemeSeparatorIndex + 3;
        var authorityEndIndex = externalUrl.length();
        for (int i = authorityStartIndex; i < externalUrl.length(); i++) {
            var c = externalUrl.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEndIndex = i;
                break;
            }
        }
        if (authorityEndIndex <= authorityStartIndex) {
            return externalUrl;
        }
        var authority = externalUrl.substring(authorityStartIndex, authorityEndIndex);
        var convertedAuthority = toAsciiAuthority(authority);
        if (convertedAuthority.equals(authority)) {
            return externalUrl;
        }
        return externalUrl.substring(0, authorityStartIndex)
                + convertedAuthority
                + externalUrl.substring(authorityEndIndex);
    }

    // 处理用户信息、IPv6 与端口，失败时保留原值。
    private static String toAsciiAuthority(String authority) {
        var atIndex = authority.lastIndexOf('@');
        var userInfo = atIndex >= 0 ? authority.substring(0, atIndex + 1) : "";
        var hostPort = atIndex >= 0 ? authority.substring(atIndex + 1) : authority;
        if (StringUtils.isBlank(hostPort)) {
            return authority;
        }
        if (hostPort.startsWith("[")) {

            return authority;
        }
        var host = hostPort;
        var port = "";
        var colonIndex = hostPort.lastIndexOf(':');
        if (colonIndex >= 0) {
            host = hostPort.substring(0, colonIndex);
            port = hostPort.substring(colonIndex);
        }
        if (StringUtils.isBlank(host)) {
            return authority;
        }
        try {
            return userInfo + IDN.toASCII(host) + port;
        } catch (IllegalArgumentException e) {

            return authority;
        }
    }
}
