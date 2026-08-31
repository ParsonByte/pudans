package cn.tolim.pudans.infra.serialization;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

public class FallbackJsonRedisSerializer implements RedisSerializer<Object> {

    private final RedisSerializer<Object> primary;

    private final RedisSerializer<Object> legacy;

    public FallbackJsonRedisSerializer(RedisSerializer<Object> primary, RedisSerializer<Object> legacy) {
        this.primary = primary;
        this.legacy = legacy;
    }

    @Override
    public byte[] serialize(Object value) {
        return primary.serialize(value);
    }

    @Override
    public Object deserialize(byte[] bytes) {
        return decode(bytes).value();
    }

    public Decoded decode(byte[] bytes) {
        if (bytes == null) {
            return new Decoded(null, false);
        }
        try {
            return new Decoded(primary.deserialize(bytes), false);
        } catch (RuntimeException primaryFailure) {
            try {
                return new Decoded(legacy.deserialize(bytes), true);
            } catch (RuntimeException legacyFailure) {
                legacyFailure.addSuppressed(primaryFailure);
                throw new SerializationException("Unable to deserialize cached value with Jackson 3 or Jackson 2",
                        legacyFailure);
            }
        }
    }

    public record Decoded(Object value, boolean legacy) {}
}
