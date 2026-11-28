package cn.tolim.pudans.communal.redis.lock;

public class LockAcquisitionException extends RuntimeException {

    private final String lockName;

    public LockAcquisitionException(String lockName) {
        super("could not acquire lock: " + lockName);
        this.lockName = lockName;
    }

    public LockAcquisitionException(String lockName, String message, Throwable cause) {
        super(message, cause);
        this.lockName = lockName;
    }

    public String getLockName() {
        return lockName;
    }
}