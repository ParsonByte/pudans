package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.util.Assert;

import java.util.concurrent.TimeUnit;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;


@SuppressWarnings({"AlibabaClassNamingShouldBeCamel",
        "AlibabaServiceOrDaoClassShouldEndWithImpl"})
public class RedisOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService {

    private static final Long TIMEOUT = 10L;
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisOAuth2AuthorizationConsentService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static String buildKey(String registeredClientId, String principalName) {
        return "token" + COLON + "consent" + COLON + registeredClientId + COLON + principalName;
    }

    private static String buildKey(OAuth2AuthorizationConsent authorizationConsent) {
        return buildKey(authorizationConsent.getRegisteredClientId(),
                authorizationConsent.getPrincipalName());
    }

    @Override
    public void save(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent 不能为空");
        redisTemplate.opsForValue().set(buildKey(authorizationConsent), authorizationConsent,
                TIMEOUT, TimeUnit.MINUTES);

    }

    @Override
    public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        Assert.notNull(authorizationConsent, "authorizationConsent 不能为空");
        redisTemplate.delete(buildKey(authorizationConsent));
    }

    @Override
    public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
        Assert.hasText(registeredClientId, "registeredClientId 不能为空");
        Assert.hasText(principalName, "principalName 不能为空");
        return (OAuth2AuthorizationConsent) redisTemplate.opsForValue()
                .get(buildKey(registeredClientId, principalName));
    }

}
