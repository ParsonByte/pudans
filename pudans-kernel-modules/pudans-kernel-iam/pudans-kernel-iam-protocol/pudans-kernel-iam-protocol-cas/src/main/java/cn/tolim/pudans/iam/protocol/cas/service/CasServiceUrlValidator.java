package cn.tolim.pudans.iam.protocol.cas.service;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.util.Set;


public final class CasServiceUrlValidator {

    private CasServiceUrlValidator() {
    }

    // Check whether the requested service URL is registered for the application.
    public static boolean isAllowed(Set<String> serviceUrls, String serviceUrl) {
        if (serviceUrls == null || serviceUrls.isEmpty() || StringUtils.isBlank(serviceUrl)) {
            return false;
        }
        String candidate = normalize(serviceUrl);
        if (candidate == null) {
            return false;
        }
        for (String service : serviceUrls) {
            String allowed = normalize(service);
            if (allowed == null) {
                continue;
            }
            if (candidate.equals(allowed) || candidate.startsWith(allowed + "/")
                    || candidate.startsWith(allowed + "?")) {
                return true;
            }
        }
        return false;
    }

    // Validate and normalize a URL, rejecting non-HTTP schemes and missing hosts.
    public static String normalize(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return null;
            }
            if (StringUtils.isBlank(uri.getHost())) {
                return null;
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String query = uri.getQuery() == null ? "" : "?" + uri.getQuery();
            return scheme.toLowerCase() + "://" + uri.getHost().toLowerCase()
                    + (uri.getPort() == -1 ? "" : ":" + uri.getPort()) + path + query;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}