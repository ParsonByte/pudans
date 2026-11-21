package cn.tolim.pudans.core.infra.properties;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "pudans.oidc")
public class OidcProperties {


    private List<Provider> providers = new ArrayList<>();


    @Data
    public static class Provider {

        private boolean enabled = true;

        private String registrationId;


        private String displayName;


        private String issuerUri;


        private String authorizationUri;


        private String tokenUri;


        private String userInfoUri;

        private String clientId;

        private String clientSecret;

        private List<String> scopes = List.of("openid", "profile", "email");


        private String userNameAttribute = "sub";
    }
}
