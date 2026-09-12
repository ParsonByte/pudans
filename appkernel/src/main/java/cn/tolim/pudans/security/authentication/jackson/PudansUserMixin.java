package cn.tolim.pudans.security.authentication.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.springframework.security.core.userdetails.UserDetails;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class PudansUserMixin {

    @JsonCreator
    public PudansUserMixin(
            @JsonProperty("delegate") UserDetails delegate,
            @JsonProperty("twoFactorAuthEnabled") boolean twoFactorAuthEnabled,
            @JsonProperty("totpEncryptedSecret") String totpEncryptedSecret) {}
}
