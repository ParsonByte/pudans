package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.infra.autoconfiguration.SupportProperties;
import cn.tolim.pudans.iam.portal.config.PortalProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class PortalUrlResolver {

    private final PortalProperties portalProperties;
    private final ObjectProvider<SupportProperties> supportPropertiesProvider;

    public PortalUrlResolver(PortalProperties portalProperties,
                             ObjectProvider<SupportProperties> supportPropertiesProvider) {
        this.portalProperties = portalProperties;
        this.supportPropertiesProvider = supportPropertiesProvider;
    }

    // Resolves the public homepage base URL
    // Parameter fallbackBaseUrl: String request derived base URL
    // Returns: String
    public String resolveHomeUrl(String fallbackBaseUrl) {
        String configured = portalProperties.getHomeBaseUrl();
        if (StringUtils.isNotBlank(configured)) {
            return trimTrailingSlash(configured.trim());
        }
        SupportProperties supportProperties = supportPropertiesProvider.getIfAvailable();
        if (supportProperties != null && supportProperties.getServer() != null
                && StringUtils.isNotBlank(supportProperties.getServer().getPortalPublicBaseUrl())) {
            return trimTrailingSlash(supportProperties.getServer().getPortalPublicBaseUrl().trim());
        }
        return trimTrailingSlash(StringUtils.defaultString(fallbackBaseUrl).trim());
    }

    // Resolves the public login page URL
    // Parameter fallbackBaseUrl: String request derived base URL
    // Returns: String
    public String resolveLoginUrl(String fallbackBaseUrl) {
        String configured = portalProperties.getLoginBaseUrl();
        if (StringUtils.isNotBlank(configured)) {
            return trimTrailingSlash(configured.trim());
        }
        return joinPath(resolveHomeUrl(fallbackBaseUrl), portalProperties.getLoginPath());
    }

    // Resolves the public global logout URL
    // Parameter fallbackBaseUrl: String request derived base URL
    // Returns: String
    public String resolveLogoutUrl(String fallbackBaseUrl) {
        String configured = portalProperties.getLogoutBaseUrl();
        if (StringUtils.isNotBlank(configured)) {
            return trimTrailingSlash(configured.trim());
        }
        return joinPath(resolveHomeUrl(fallbackBaseUrl), portalProperties.getLogoutPath());
    }

    // Joins a base URL and a path
    // Parameter baseUrl: String base URL
    // Parameter path: String path, absolute paths keep their leading slash
    // Returns: String
    public static String joinPath(String baseUrl, String path) {
        String base = trimTrailingSlash(StringUtils.defaultString(baseUrl));
        if (StringUtils.isBlank(path)) {
            return base;
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        return base + normalized;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}