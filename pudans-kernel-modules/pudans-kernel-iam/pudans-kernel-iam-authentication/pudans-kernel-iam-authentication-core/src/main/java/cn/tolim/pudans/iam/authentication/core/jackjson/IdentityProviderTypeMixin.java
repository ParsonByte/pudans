package cn.tolim.pudans.iam.authentication.core.jackjson;

import com.fasterxml.jackson.annotation.*;


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class IdentityProviderTypeMixin {

    @JsonCreator
    IdentityProviderTypeMixin(@JsonProperty("value") String value,
                              @JsonProperty("name") String name,
                              @JsonProperty("desc") String desc) {
    }
}
