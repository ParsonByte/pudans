package cn.tolim.pudans.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "pudans.feishu")
public class FeishuProperties {


    private boolean enabled;


    private String appId;


    private String appSecret;


    private String redirectUri =
            "{baseUrl}/apis/auth.api.pudans.tolim.cn/v1alpha1/oauth2/callback/feishu";


    private String authorizationUri = "https://open.feishu.cn/open-apis/authen/v1/authorize";


    private String scope = "contact:user.base:readonly contact:user.email:readonly";


    private long syncIntervalSeconds = 21600;

    private String alertChatId = "";
}
