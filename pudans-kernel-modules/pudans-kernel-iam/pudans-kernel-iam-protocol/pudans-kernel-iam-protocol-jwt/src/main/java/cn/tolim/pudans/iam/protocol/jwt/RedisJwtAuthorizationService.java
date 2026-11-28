
package cn.tolim.pudans.iam.protocol.jwt;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.jackjson.SupportJackson2Module;
import cn.tolim.pudans.iam.protocol.jwt.jackson.JwtAuthorizationModule;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.http.converter.json.SpringHandlerInstantiator;
import org.springframework.util.Assert;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.JWT_PROTOCOL_CACHE_PREFIX;


public class RedisJwtAuthorizationService extends AbstractJwtAuthorizationService {
    private static final String CID_TO_AUTHORIZATIONS = "cid_to_authorizations:";
    private static final String UID_TO_AUTHORIZATIONS = "uid_to_authorizations:";
    private static final String ID_TO_AUTHORIZATION = "id_to_authorization:";
    private static final String ID_TO_CORRELATIONS = "id_to_correlations:";
    private static final MessageDigest DIGEST;

    static {
        try {
            DIGEST = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final RedisOperations<String, String> redisOperations;
    @Setter
    private String prefix = JWT_PROTOCOL_CACHE_PREFIX;
    @Setter
    private ObjectMapper objectMapper = new ObjectMapper();

    public RedisJwtAuthorizationService(RedisOperations<String, String> redisOperations,
                                        AutowireCapableBeanFactory beanFactory,
                                        ApplicationServiceLoader applicationServiceLoader) {
        super(applicationServiceLoader);
        Assert.notNull(redisOperations, "redisOperations 不能为空");
        this.redisOperations = redisOperations;
        ClassLoader classLoader = this.getClass().getClassLoader();
        objectMapper.registerModules(SupportJackson2Module.getModules(classLoader));
        objectMapper.registerModule(new JwtAuthorizationModule());
        objectMapper.setHandlerInstantiator(new SpringHandlerInstantiator(beanFactory));
    }

    protected static String generateKey(String rawKey) {
        byte[] bytes = DIGEST.digest(rawKey.getBytes(StandardCharsets.UTF_8));
        return String.format("%032x", new BigInteger(1, bytes));
    }

    @Override
    public void save(JwtAuthentication token) {
        String authorizationId = token.getId();
        String uidToAuthorizationsKey = getIdTokenToAuthorization(authorizationId);
        String idToAuthorizationKey = getIdToAuthorizationKey(authorizationId);
        String idToCorrelationsKey = getIdToCorrelations(authorizationId);
        String cidToAuthorizationsKey = getCidToAuthorizations(token.getClientId());

        Duration timeToLive = Duration.between(token.getIssuedAt(), token.getExpiresAt());
        Set<String> correlationValues = new HashSet<>();
        // add client authorizations
        correlationValues.add(cidToAuthorizationsKey);
        redisOperations.opsForSet().add(cidToAuthorizationsKey, authorizationId);
        redisOperations.expire(cidToAuthorizationsKey, timeToLive);
        // save authorization
        correlationValues.add(idToAuthorizationKey);
        redisOperations.opsForValue().set(idToAuthorizationKey, write(token));
        redisOperations.expire(idToAuthorizationKey, timeToLive);
        // save id_token
        correlationValues.add(uidToAuthorizationsKey);
        redisOperations.opsForValue().set(uidToAuthorizationsKey, token.getToken());
        redisOperations.expire(uidToAuthorizationsKey, timeToLive);
        // save correlations
        correlationValues.add(idToCorrelationsKey);
        redisOperations.opsForSet().add(idToCorrelationsKey,
                correlationValues.toArray(String[]::new));
        redisOperations.expire(idToCorrelationsKey, timeToLive);
    }

    @Override
    public void remove(JwtAuthentication authorization) {
        String authorizationId = authorization.getId();
        String uidToAuthorizationsKey = getIdTokenToAuthorization(authorizationId);
        String idToAuthorizationKey = getIdToAuthorizationKey(authorizationId);
        String idToCorrelationsKey = getIdToCorrelations(authorizationId);
        String cidToAuthorizationsKey = getCidToAuthorizations(authorization.getClientId());
        redisOperations.delete(idToAuthorizationKey);
        redisOperations.delete(idToCorrelationsKey);
        redisOperations.delete(uidToAuthorizationsKey);
        redisOperations.opsForSet().remove(cidToAuthorizationsKey, authorization.getId());
    }

    @Override
    public JwtAuthentication findById(String id) {
        return Optional.ofNullable(redisOperations.opsForValue().get(getIdToAuthorizationKey(id)))
                .map(this::parse).orElse(null);
    }

    @Override
    public JwtAuthentication findByToken(String token) {
        return Optional
                .ofNullable(redisOperations.opsForValue().get(getIdTokenToAuthorization(token)))
                .map(this::parse).orElse(null);
    }

    private String getIdToCorrelations(String authorizationId) {
        return prefix + ID_TO_CORRELATIONS + authorizationId;
    }

    private String write(Object data) {
        try {
            return this.objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    private JwtAuthentication parse(String data) {
        try {
            return this.objectMapper.readValue(data, new TypeReference<>() {
            });
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex.getMessage(), ex);
        }
    }

    public String getCidToAuthorizations(String clientId) {
        return prefix + CID_TO_AUTHORIZATIONS + clientId;
    }

    private String getIdToAuthorizationKey(String authorizationId) {
        return prefix + ID_TO_AUTHORIZATION + authorizationId;
    }

    private String getIdTokenToAuthorization(String idToken) {
        return prefix + UID_TO_AUTHORIZATIONS + generateKey(idToken);
    }
}
