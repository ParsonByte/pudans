package cn.tolim.pudans.iam.identitysource.feishu.domain.response;

import cn.tolim.pudans.iam.identitysource.feishu.domain.BaseResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;


@EqualsAndHashCode(callSuper = true)
@Data
@ToString(callSuper = true)
public class GetAccessTokenResponse extends BaseResponse {
    // Access token
    @JsonProperty("tenant_access_token")
    private String tenantAccessToken;

    // Token expiration time in seconds
    private int expire;
}
