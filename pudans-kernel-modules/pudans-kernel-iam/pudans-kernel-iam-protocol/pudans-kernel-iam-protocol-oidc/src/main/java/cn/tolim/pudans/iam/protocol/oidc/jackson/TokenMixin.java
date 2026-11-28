package cn.tolim.pudans.iam.protocol.oidc.jackson;

import com.fasterxml.jackson.annotation.*;
import org.springframework.security.oauth2.core.OAuth2Token;

import java.util.Map;


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class TokenMixin {

    @JsonCreator
    TokenMixin(@JsonProperty("token") OAuth2Token token,
               @JsonProperty("metadata") Map<String, Object> metadata) {
    }

}
