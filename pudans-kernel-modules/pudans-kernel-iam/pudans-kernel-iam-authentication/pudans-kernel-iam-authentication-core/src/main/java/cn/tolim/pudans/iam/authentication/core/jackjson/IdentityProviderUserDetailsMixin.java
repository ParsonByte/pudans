package cn.tolim.pudans.iam.authentication.core.jackjson;

import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;


@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonDeserialize(builder = IdentityProviderUserDetails.IdpUserDetailsBuilder.class)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class IdentityProviderUserDetailsMixin {

}
