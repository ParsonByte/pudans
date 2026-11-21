package cn.tolim.pudans.core.ai.routing.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiRouteGroup;
import org.junit.jupiter.api.Test;

class RouteStrategyRegistryTest {

    private final RouteStrategyRegistry registry = new RouteStrategyRegistry();

    @Test
    void registersEveryBuiltinMode() {
        assertThat(AiRouteGroup.RouteMode.values()).allSatisfy(mode -> assertThat(registry.strategyFor(mode).mode())
                .isEqualTo(mode));
    }

    @Test
    void fallsBackToSmartForNullMode() {
        assertThat(registry.strategyFor(null).mode()).isEqualTo(AiRouteGroup.RouteMode.SMART);
    }
}
