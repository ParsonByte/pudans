package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreaker;
import cn.tolim.pudans.core.ai.routing.circuit.CircuitBreakerConfig;
import cn.tolim.pudans.core.ai.routing.circuit.InMemoryCircuitBreaker;
import cn.tolim.pudans.core.ai.routing.classifier.ComplexityClassifier;
import cn.tolim.pudans.core.ai.routing.classifier.HeuristicComplexityClassifier;
import cn.tolim.pudans.core.ai.routing.strategy.RouteStrategyRegistry;
import cn.tolim.pudans.infra.properties.AiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public CircuitBreaker circuitBreaker(AiProperties properties) {
        var circuit = properties.getRouting().getCircuit();
        return new InMemoryCircuitBreaker(new CircuitBreakerConfig(
                circuit.getFailureThreshold(),
                circuit.getSuccessThreshold(),
                circuit.getOpenDuration(),
                circuit.getMaxOpenDuration(),
                circuit.getMaxLatencyMillis()));
    }

    @Bean
    public RouteEngine routeEngine(
            RouteStrategyRegistry strategies, CircuitBreaker circuitBreaker, ComplexityClassifier classifier) {
        return new RouteEngine(strategies, circuitBreaker, classifier);
    }
}
