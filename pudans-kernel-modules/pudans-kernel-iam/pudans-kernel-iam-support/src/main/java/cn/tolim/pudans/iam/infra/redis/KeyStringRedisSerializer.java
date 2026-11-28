package cn.tolim.pudans.iam.infra.redis;

import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;

public final class KeyStringRedisSerializer implements RedisSerializer<String> {
    private final String keyPrefix;

    public KeyStringRedisSerializer(String keyPrefix) {
        this.keyPrefix = keyPrefix + ":";
    }

    @Override
    public byte[] serialize(String value) {
        if (value == null) {
            return null;
        }
        return (keyPrefix + value).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String deserialize(byte[] value) {
        if (value == null) {
            return null;
        }
        String key = new String(value, StandardCharsets.UTF_8);
        return key.startsWith(keyPrefix) ? key.substring(keyPrefix.length()) : key;
    }
}
