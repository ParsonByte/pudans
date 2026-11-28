package cn.tolim.pudans.communal.redis.lock;

public interface LockHandle extends AutoCloseable {

    String name();

    @Override
    void close();
}