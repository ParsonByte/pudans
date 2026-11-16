package cn.tolim.pudans.contract.infra.utils;

import java.net.URI;
import java.net.URISyntaxException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;


@Slf4j
@UtilityClass
public class PathUtils {


    public static boolean isAbsoluteUri(final String uriString) {
        if (StringUtils.isBlank(uriString)) {
            return false;
        }
        try {
            URI uri = new URI(uriString);
            return uri.isAbsolute();
        } catch (URISyntaxException e) {
            log.debug("无法解析 URI：" + uriString, e);

            return false;
        }
    }


    public static String combinePath(String... pathSegments) {
        StringBuilder sb = new StringBuilder();
        for (String path : pathSegments) {
            if (path == null) {
                continue;
            }
            String s = path.startsWith("/") ? path : "/" + path;
            String segment = s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
            sb.append(segment);
        }
        return sb.toString();
    }


    public static String appendPathSeparatorIfMissing(String path) {
        return StringUtils.appendIfMissing(path, "/", "/");
    }


    public static String simplifyPathPattern(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return StringUtils.EMPTY;
        }
        String[] parts = StringUtils.split(pattern, '/');
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.startsWith("{") && part.endsWith("}")) {
                int colonIdx = part.indexOf(':');
                if (colonIdx != -1) {
                    parts[i] = part.substring(0, colonIdx) + part.charAt(part.length() - 1);
                }
            }
        }
        return combinePath(parts);
    }
}
