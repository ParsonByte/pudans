package cn.tolim.pudans.iam.authentication.core.jackjson;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import com.fasterxml.jackson.annotation.*;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, creatorVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class IdentityProviderAuthenticationTokenMixin {
    @JsonCreator
    IdentityProviderAuthenticationTokenMixin(@JsonProperty("principal") Object principal,
                                             @JsonProperty("providerType") IdentityProviderType providerType,
                                             @JsonProperty("providerId") String providerId,
                                             @JsonProperty("authorities") Collection<? extends GrantedAuthority> authorities) {
    }
}
