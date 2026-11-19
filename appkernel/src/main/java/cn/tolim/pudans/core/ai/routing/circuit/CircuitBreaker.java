package cn.tolim.pudans.core.ai.routing.circuit;

public interface CircuitBreaker {

    CircuitState state(String key);

    default boolean isOpen(String key) {
        return state(key) == CircuitState.OPEN;
    }

    default void recordSuccess(String key) {
        recordSuccess(key, -1);
    }

    void recordSuccess(String key, long latencyMillis);

    void recordFailure(String key, CircuitFailure failure);

    double healthScore(String key);
}
