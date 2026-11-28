package cn.tolim.pudans.iam.protocol.oidc.constant;

import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;
import static cn.tolim.pudans.iam.protocol.core.constant.ProtocolConstants.PROTOCOL_CACHE_PREFIX;


public class OidcProtocolConstants {

    // Protocol cache prefix
    public static final String OIDC_PROTOCOL_CACHE_PREFIX = PROTOCOL_CACHE_PREFIX + "oidc"
            + COLON;

    public static final OAuth2TokenType ID_TOKEN = new OAuth2TokenType(
            "id_token");

    public static final String OIDC_ERROR_URI = "https://iam.pudans.cn/docs/application/oidc/faq/#%E9%94%99%E8%AF%AF%E7%A0%81";

    public final class ConfigurationSettingNames {
        private static final String SETTINGS_NAMESPACE = "settings.";

        private ConfigurationSettingNames() {
        }

        // The names for token configuration settings.
        public static final class Token {
            private static final String TOKEN_SETTINGS_NAMESPACE = SETTINGS_NAMESPACE
                    .concat("token.");

            // id_token extension field
            public static final String ID_TOKEN_CUSTOM_CLAIMS = TOKEN_SETTINGS_NAMESPACE
                    .concat("id-token-custom-claims");

            private Token() {
            }

        }

    }
}
