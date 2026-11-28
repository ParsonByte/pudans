package cn.tolim.pudans.iam.infra.autoconfiguration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = SupportProperties.DEFAULT_PREFIX)
public class SupportProperties {
    public static final String DEFAULT_PREFIX = "pudans";

    @NestedConfigurationProperty
    private final Server server = new Server();
    @NestedConfigurationProperty
    private Security security = new Security();

    public Server getServer() {
        return server;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public static class Server {
        private String consolePublicBaseUrl;
        private String portalPublicBaseUrl;
        private String openApiPublicBaseUrl;

        public String getConsolePublicBaseUrl() {
            return consolePublicBaseUrl;
        }

        public void setConsolePublicBaseUrl(String value) {
            this.consolePublicBaseUrl = value;
        }

        public String getPortalPublicBaseUrl() {
            return portalPublicBaseUrl;
        }

        public void setPortalPublicBaseUrl(String value) {
            this.portalPublicBaseUrl = value;
        }

        public String getOpenApiPublicBaseUrl() {
            return openApiPublicBaseUrl;
        }

        public void setOpenApiPublicBaseUrl(String value) {
            this.openApiPublicBaseUrl = value;
        }
    }

    public static class Security {
        @NestedConfigurationProperty
        private Jump jump = new Jump();

        public Jump getJump() {
            return jump;
        }

        public void setJump(Jump jump) {
            this.jump = jump;
        }

        public static class Jump {
            private String defaultRedirectUrl;

            public String getDefaultRedirectUrl() {
                return defaultRedirectUrl;
            }

            public void setDefaultRedirectUrl(String value) {
                this.defaultRedirectUrl = value;
            }
        }
    }
}
