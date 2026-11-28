package cn.tolim.pudans.iam.protocol.saml.store;

import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.util.Assert;

import java.time.Duration;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_PROTOCOL_CACHE_PREFIX;


public class RedisSamlAuthnRequestStore implements SamlAuthnRequestStore {

    private static final String AUTHN_REQUEST_KEY = "authn_request:";

    private final RedisOperations<String, String> redisOperations;
    @Setter
    private String prefix = SAML_PROTOCOL_CACHE_PREFIX;
    @Setter
    private ObjectMapper objectMapper = new ObjectMapper();

    public RedisSamlAuthnRequestStore(RedisOperations<String, String> redisOperations) {
        Assert.notNull(redisOperations, "redisOperations must not be null");
        this.redisOperations = redisOperations;
    }

    @Override
    public void save(SamlAuthnRequest authnRequest, Duration timeToLive) {
        Assert.notNull(authnRequest, "authnRequest must not be null");
        Assert.hasText(authnRequest.getId(), "authnRequest id must not be blank");
        Duration ttl = timeToLive == null ? Duration.ofMinutes(5) : timeToLive;
        redisOperations.opsForValue().set(key(authnRequest.getId()), write(authnRequest), ttl);
    }

    @Override
    public SamlAuthnRequest find(String id) {
        if (id == null) {
            return null;
        }
        return read(redisOperations.opsForValue().get(key(id)));
    }

    @Override
    public SamlAuthnRequest consume(String id) {
        if (id == null) {
            return null;
        }
        return read(redisOperations.opsForValue().getAndDelete(key(id)));
    }

    @Override
    public void remove(String id) {
        if (id != null) {
            redisOperations.delete(key(id));
        }
    }

    private String key(String id) {
        return prefix + AUTHN_REQUEST_KEY + id;
    }

    private String write(SamlAuthnRequest authnRequest) {
        try {
            return objectMapper.writeValueAsString(authnRequest);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }

    private SamlAuthnRequest read(String value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readValue(value, SamlAuthnRequest.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException(exception.getMessage(), exception);
        }
    }
}