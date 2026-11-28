package cn.tolim.pudans.iam.protocol.oidc.jackson;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@SuppressWarnings("AlibabaAbstractClassShouldStartWithAbstractNaming")
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
abstract class TokenTypeMixin {

    @JsonCreator
    TokenTypeMixin(@JsonProperty("value") String value) {
    }

}
