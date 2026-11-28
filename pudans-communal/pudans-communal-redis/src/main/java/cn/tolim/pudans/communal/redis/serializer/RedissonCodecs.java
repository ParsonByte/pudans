package cn.tolim.pudans.communal.redis.serializer;

import org.redisson.client.codec.Codec;
import org.redisson.codec.JsonJackson3Codec;
import tools.jackson.databind.ObjectMapper;

// Redisson codecs aligned with the Jackson 3 runtime.
public final class RedissonCodecs {

    private RedissonCodecs() {
    }

    public static Codec json() {
        return new JsonJackson3Codec();
    }

    public static Codec json(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("objectMapper must not be null");
        }
        return new JsonJackson3Codec(objectMapper);
    }
}