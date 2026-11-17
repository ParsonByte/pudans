package cn.tolim.pudans.infra.properties;

import cn.tolim.pudans.contract.extension.ApiGroups;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;


@Data
@ConfigurationProperties(prefix = "pudans.feishu")
public class FeishuProperties {


    private boolean enabled;


    private String appId;


    private String appSecret;


    private String redirectUri =
            "{baseUrl}" + ApiGroups.API_PREFIX + "/oauth2/callback/feishu";


    private String authorizationUri = "https://open.feishu.cn/open-apis/authen/v1/authorize";


    private String scope = "contact:user.base:readonly contact:user.email:readonly";


    private long syncIntervalSeconds = 21600;

    private String alertChatId = "";

    public boolean isConfigured() {
        return enabled && StringUtils.hasText(appId) && StringUtils.hasText(appSecret);
    }
}
