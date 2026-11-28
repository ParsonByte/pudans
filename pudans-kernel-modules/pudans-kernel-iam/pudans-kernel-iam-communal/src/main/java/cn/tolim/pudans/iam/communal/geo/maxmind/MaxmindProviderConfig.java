package cn.tolim.pudans.iam.communal.geo.maxmind;

import cn.tolim.pudans.iam.communal.geo.GeoLocationProviderConfig;
import cn.tolim.pudans.iam.communal.jackjson.encrypt.JsonPropertyEncrypt;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;


@EqualsAndHashCode(callSuper = true)
@Data
public class MaxmindProviderConfig extends GeoLocationProviderConfig.GeoLocationConfig {
    // Password
    @JsonPropertyEncrypt
    @NotEmpty(message = "密码不能为空")
    private String sessionKey;
}
