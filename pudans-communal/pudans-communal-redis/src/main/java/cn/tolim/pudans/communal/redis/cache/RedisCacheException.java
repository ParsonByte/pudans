package cn.tolim.pudans.communal.redis.cache;

public class RedisCacheException extends RuntimeException {

    public RedisCacheException(String message) {
        super(message);
    }

    public RedisCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}