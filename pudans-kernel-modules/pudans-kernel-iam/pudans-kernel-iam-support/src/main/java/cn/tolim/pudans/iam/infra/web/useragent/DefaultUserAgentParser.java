package cn.tolim.pudans.iam.infra.web.useragent;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DefaultUserAgentParser implements UserAgentParser {
    private static final Pattern VERSION = Pattern.compile("(\\d+)(?:\\.(\\d+))?");

    @Override
    public UserAgent getUserAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (value == null) {
            value = "";
        }
        String platform = value.contains("Windows") ? "Windows"
                : value.contains("Mac OS X") ? "macOS" : value.contains("Android") ? "Android"
                : value.contains("iPhone") || value.contains("iPad") ? "iOS"
                : value.contains("Linux") ? "Linux" : "Unknown";
        String browser = value.contains("Edg/") ? "Edge"
                : value.contains("Chrome/") ? "Chrome"
                : value.contains("Firefox/") ? "Firefox"
                : value.contains("Safari/") ? "Safari" : "Unknown";
        String deviceType = value.contains("Mobile") ? "Mobile"
                : value.contains("Tablet") ? "Tablet" : "Desktop";
        String version = browserVersion(value, browser);
        return UserAgent.builder().browser(browser).browserType(browser).browserMajorVersion(version)
                .deviceType(deviceType).platform(platform).platformVersion(platformVersion(value))
                .renderingEngineMaker(value.contains("AppleWebKit") ? "WebKit" : "Unknown").build();
    }

    private String browserVersion(String userAgent, String browser) {
        String prefix = "Edge".equals(browser) ? "Edg/"
                : "Chrome".equals(browser) ? "Chrome/" : browser + "/";
        int index = userAgent.indexOf(prefix);
        if (index < 0) {
            return null;
        }
        Matcher matcher = VERSION.matcher(userAgent.substring(index + prefix.length()));
        if (matcher.find()) {
            return matcher.group(2) == null ? matcher.group(1)
                    : matcher.group(1) + "." + matcher.group(2);
        }
        return null;
    }

    private String platformVersion(String userAgent) {
        for (String prefix : new String[]{"Windows NT ", "Android ", "iPhone OS ", "Mac OS X "}) {
            int index = userAgent.indexOf(prefix);
            if (index >= 0) {
                Matcher matcher = VERSION.matcher(userAgent.substring(index + prefix.length()));
                if (matcher.find()) {
                    return matcher.group(2) == null ? matcher.group(1)
                            : matcher.group(1) + "." + matcher.group(2);
                }
            }
        }
        return null;
    }
}
