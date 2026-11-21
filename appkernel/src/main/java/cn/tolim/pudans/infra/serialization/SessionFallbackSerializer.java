package cn.tolim.pudans.infra.serialization;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.serializer.RedisSerializer;

@Slf4j
public class SessionFallbackSerializer implements RedisSerializer<Object> {

    private static final int JDK_MAGIC_FIRST = 0xAC;

    private static final int JDK_MAGIC_SECOND = 0xED;

    private static final AtomicBoolean JDK_WARNING_LOGGED = new AtomicBoolean();

    private final RedisSerializer<Object> primary;

    private final RedisSerializer<Object> legacyJson;

    public SessionFallbackSerializer(RedisSerializer<Object> primary, RedisSerializer<Object> legacyJson) {
        this.primary = primary;
        this.legacyJson = legacyJson;
    }

    @Override
    public byte[] serialize(Object value) {
        return primary.serialize(value);
    }

    @Override
    public Object deserialize(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (isJdkSerialized(bytes)) {
            if (JDK_WARNING_LOGGED.compareAndSet(false, true)) {
                log.warn("忽略旧版 JDK 序列化的会话值；JDK 反序列化已禁用。");
            }
            return null;
        }
        try {
            return primary.deserialize(bytes);
        } catch (RuntimeException primaryFailure) {
            try {
                return legacyJson.deserialize(bytes);
            } catch (RuntimeException legacyFailure) {
                legacyFailure.addSuppressed(primaryFailure);
                throw legacyFailure;
            }
        }
    }

    public static boolean isJdkSerialized(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xFF) == JDK_MAGIC_FIRST && (bytes[1] & 0xFF) == JDK_MAGIC_SECOND;
    }
}
