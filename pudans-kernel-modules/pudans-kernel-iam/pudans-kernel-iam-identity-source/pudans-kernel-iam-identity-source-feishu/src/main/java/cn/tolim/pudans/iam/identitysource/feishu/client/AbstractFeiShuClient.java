package cn.tolim.pudans.iam.identitysource.feishu.client;

import cn.tolim.pudans.iam.identitysource.core.client.AbstractIdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.exception.ApiCallException;
import cn.tolim.pudans.iam.identitysource.feishu.FeiShuConfig;
import cn.tolim.pudans.iam.identitysource.feishu.domain.request.GetAccessTokenRequest;
import cn.tolim.pudans.iam.identitysource.feishu.domain.response.GetAccessTokenResponse;
import com.alibaba.fastjson2.JSON;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.http.*;
import org.springframework.util.Assert;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

import static cn.tolim.pudans.iam.identitysource.feishu.FeiShuConstant.ACCESS_TOKEN_URL;
import static cn.tolim.pudans.iam.identitysource.feishu.FeiShuConstant.LOGGER_NAME;


@SuppressWarnings("DuplicatedCode")
@Slf4j(topic = LOGGER_NAME)
public abstract class AbstractFeiShuClient extends AbstractIdentitySourceClient<FeiShuConfig> {

    protected RestOperations restOperations;
    // Cache
    private Cache<String, String> cache;

    protected AbstractFeiShuClient(FeiShuConfig config) {
        super(config);
        this.restOperations = new RestTemplate();
        String token = getAccessToken();
        cache.put(ACCESS_KEY, token);
    }

    // Get access token
    // Returns String
    protected String getAccessToken() {
        if (ObjectUtils.isNotEmpty(cache)
                && ObjectUtils.isNotEmpty(cache.getIfPresent(ACCESS_KEY))) {
            return cache.getIfPresent(ACCESS_KEY);
        }
        GetAccessTokenRequest request = new GetAccessTokenRequest(getConfig().getAppId(),
                getConfig().getAppSecret());
        log.debug("获取飞书 Access Token 入参: {}", JSON.toJSONString(request));
        GetAccessTokenResponse response = postToken(request);
        Assert.notNull(response, "获取Token返回结果为空");
        log.debug("获取飞书 Access Token 返回: {}", JSON.toJSONString(response));
        if (response.isSuccess()) {
            cache = CacheBuilder.newBuilder()
                    .concurrencyLevel(Runtime.getRuntime().availableProcessors())
                    .expireAfterWrite(response.getExpire(), TimeUnit.SECONDS).build();
            cache.put(ACCESS_KEY, response.getTenantAccessToken());
            return cache.getIfPresent(ACCESS_KEY);
        }
        log.error("获取飞书 Access Token 失败: {}", JSON.toJSONString(response));
        throw new ApiCallException("获取飞书 Access Token 失败");
    }

    // Get token
    // Parameter json Object
    // Returns GetAccessTokenResponse
    private GetAccessTokenResponse postToken(Object json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Object> requestEntity = new HttpEntity<>(JSON.toJSONString(json), headers);
        ResponseEntity<GetAccessTokenResponse> response = restOperations.exchange(ACCESS_TOKEN_URL,
                HttpMethod.POST, requestEntity, GetAccessTokenResponse.class);
        return response.getBody();
    }

    // Set RestOperations
    public void setRestOperations(RestOperations restOperations) {
        Assert.notNull(restOperations, "restOperations 不能为空");
        this.restOperations = restOperations;
    }
}
