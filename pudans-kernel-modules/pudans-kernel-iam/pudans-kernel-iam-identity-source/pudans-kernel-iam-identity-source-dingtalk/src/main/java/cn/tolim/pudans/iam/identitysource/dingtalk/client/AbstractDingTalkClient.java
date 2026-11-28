package cn.tolim.pudans.iam.identitysource.dingtalk.client;

import cn.tolim.pudans.iam.identitysource.core.client.AbstractIdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConfig;
import com.alibaba.fastjson2.JSON;
import com.aliyun.dingtalkoauth2_1_0.Client;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;

import java.util.concurrent.TimeUnit;

import static cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConstants.LOGGER_NAME;


@Slf4j(topic = LOGGER_NAME)
public abstract class AbstractDingTalkClient extends AbstractIdentitySourceClient<DingTalkConfig> {
    // Cache
    private Cache<String, String> cache;

    protected AbstractDingTalkClient(DingTalkConfig config) {
        super(config);
        String token = getAccessToken();
        cache.put(ACCESS_KEY, token);
    }

    // Initialize an account client with a token
    public static Client createClient() {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        try {
            return new Client(config);
        } catch (Exception e) {
            log.error("钉钉获取Client异常：{}", e.getMessage());
            throw new ApiCallException(e.getMessage());
        }
    }

    // Get access token
    // Returns String
    protected String getAccessToken() {
        try {
            DingTalkConfig config = getConfig();
            if (ObjectUtils.isNotEmpty(cache)
                    && ObjectUtils.isNotEmpty(cache.getIfPresent(ACCESS_KEY))) {
                return cache.getIfPresent(ACCESS_KEY);
            }
            Client client = createClient();
            GetAccessTokenRequest request = new GetAccessTokenRequest()
                    .setAppKey(config.getAppKey()).setAppSecret(config.getAppSecret());
            log.debug("获取钉钉 Access Token 入参: {}", JSON.toJSONString(request));
            GetAccessTokenResponse accessToken = client.getAccessToken(request);
            log.debug("获取钉钉 Access Token 返回: {}", JSON.toJSONString(accessToken));
            GetAccessTokenResponseBody body = accessToken.getBody();
            cache = CacheBuilder.newBuilder()
                    .concurrencyLevel(Runtime.getRuntime().availableProcessors())
                    .expireAfterWrite(body.getExpireIn(), TimeUnit.SECONDS).build();
            cache.put(ACCESS_KEY, body.getAccessToken());
            return cache.getIfPresent(ACCESS_KEY);
        } catch (Exception e) {
            log.error("获取钉钉 Access Token 失败: {}", e.getMessage());
            throw new ApiCallException("获取钉钉 Access Token 失败");
        }
    }

}
