package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreakerConfig;
import cn.tolim.pudans.core.ai.routing.circuit.InMemoryCircuitBreaker;
import cn.tolim.pudans.core.ai.routing.circuit.RedisCircuitBreaker;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityClassifier;
import cn.tolim.pudans.core.ai.routing.classifier.HeuristicComplexityClassifier;
import cn.tolim.pudans.core.ai.routing.strategy.RouteStrategyRegistry;
import cn.tolim.pudans.core.infra.properties.AiProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

@Configuration
public class AiRoutingConfiguration {

    @Bean
    public RouteStrategyRegistry routeStrategyRegistry() {
        return new RouteStrategyRegistry();
    }

    @Bean
    public ComplexityClassifier complexityClassifier() {
        return new HeuristicComplexityClassifier();
    }

    @Bean
    public CircuitBreaker circuitBreaker(
            AiProperties properties, ObjectProvider<ReactiveStringRedisTemplate> redisTemplate) {
        var circuit = properties.getRouting().getCircuit();
        var config = new CircuitBreakerConfig(
                circuit.getFailureThreshold(),
                circuit.getSuccessThreshold(),
                circuit.getOpenDuration(),
                circuit.getMaxOpenDuration(),
                circuit.getMaxLatencyMillis());
        var redis = redisTemplate.getIfAvailable();
        return redis == null ? new InMemoryCircuitBreaker(config) : new RedisCircuitBreaker(config, redis);
    }

    @Bean
    public RouteEngine routeEngine(
            RouteStrategyRegistry strategies, CircuitBreaker circuitBreaker, ComplexityClassifier classifier) {
        return new RouteEngine(strategies, circuitBreaker, classifier);
    }
}
