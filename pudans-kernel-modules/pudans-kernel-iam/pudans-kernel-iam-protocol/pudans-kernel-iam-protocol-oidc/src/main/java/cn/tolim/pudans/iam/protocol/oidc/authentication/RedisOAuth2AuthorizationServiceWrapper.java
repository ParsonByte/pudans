package cn.tolim.pudans.iam.protocol.oidc.authentication;

import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

import java.util.Objects;


@SuppressWarnings({"AlibabaServiceOrDaoClassShouldEndWithImpl",
        "AlibabaClassNamingShouldBeCamel"})
public class RedisOAuth2AuthorizationServiceWrapper extends RedisOAuth2AuthorizationService {

    public RedisOAuth2AuthorizationServiceWrapper(RedisOperations<String, String> redisOperations,
                                                  RegisteredClientRepository clientRepository) {
        super(redisOperations, clientRepository);
    }

    // Saves the OAuth2Authorization.
    // Parameter authorization the OAuth2Authorization
    @Override
    public void save(OAuth2Authorization authorization) {
        String appId = ApplicationContextHolder.getApplicationContext().getAppId();
        if (authorization.getRegisteredClientId().equals(String.valueOf(appId))) {
            super.save(authorization);
        }
    }

    // Removes the OAuth2Authorization.
    // Parameter authorization the OAuth2Authorization
    @Override
    public void remove(OAuth2Authorization authorization) {
        String appId = ApplicationContextHolder.getApplicationContext().getAppId();
        if (authorization.getRegisteredClientId().equals(String.valueOf(appId))) {
            super.remove(authorization);
        }
    }

    // Returns the OAuth2Authorization identified by the provided id,
    // or null if not found.
    // Parameter id the authorization identifier
    // Returns the OAuth2Authorization if found, otherwise null
    @Override
    public OAuth2Authorization findById(String id) {
        OAuth2Authorization authorization = super.findById(id);
        if (!Objects.isNull(authorization)) {
            String appId = ApplicationContextHolder.getApplicationContext().getAppId();
            if (authorization.getRegisteredClientId().equals(String.valueOf(appId))) {
                return authorization;
            }
        }
        return null;
    }

    // Returns the OAuth2Authorization containing the provided token,
    // or null if not found.
    // Parameter token the token credential
    // Parameter tokenType the token type
    // Returns the OAuth2Authorization if found, otherwise null
    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        OAuth2Authorization authorization = super.findByToken(token, tokenType);
        if (!Objects.isNull(authorization)) {
            String appId = ApplicationContextHolder.getApplicationContext().getAppId();
            if (authorization.getRegisteredClientId().equals(String.valueOf(appId))) {
                return authorization;
            }
        }
        return null;
    }
}
