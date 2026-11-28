package cn.tolim.pudans.iam.portal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = PortalProperties.PREFIX)
public class PortalProperties {

    public static final String PREFIX = "pudans.portal";

    // Public homepage base URL resolved by the reverse proxy
    private String homeBaseUrl;

    // Public login page URL, defaults to the homepage base URL plus the login path
    private String loginBaseUrl;

    // Public global logout URL, defaults to the homepage base URL plus the logout path
    private String logoutBaseUrl;

    // Login page path appended to the homepage base URL
    private String loginPath = "/login";

    // Global logout path appended to the homepage base URL
    private String logoutPath = "/logout";

    // Default branding title shown on the login page
    private String brandingTitle = "PUDANS";
}