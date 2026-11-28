package cn.tolim.pudans.iam.authentication.core.jackjson;

import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import com.fasterxml.jackson.annotation.*;


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, creatorVisibility = JsonAutoDetect.Visibility.ANY)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class IdentityProviderNotBindAuthenticationTokenMixin {
    @JsonCreator
    IdentityProviderNotBindAuthenticationTokenMixin(@JsonProperty("principal") IdentityProviderUserDetails principal) {
    }
}
