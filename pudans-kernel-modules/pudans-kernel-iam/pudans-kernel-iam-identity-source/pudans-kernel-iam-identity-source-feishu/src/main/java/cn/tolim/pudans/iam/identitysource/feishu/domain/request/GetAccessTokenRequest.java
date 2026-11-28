package cn.tolim.pudans.iam.identitysource.feishu.domain.request;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;


@Data
@AllArgsConstructor
public class GetAccessTokenRequest implements Serializable {
    // appid
    @JSONField(name = "app_id")
    private String appId;
    // secret
    @JSONField(name = "app_secret")
    private String appSecret;
}
