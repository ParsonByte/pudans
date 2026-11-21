package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.ai.resource.AiRouteGroup;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class RouteStrategyRegistry {

    private final Map<AiRouteGroup.RouteMode, RouteStrategy> strategies;

    public RouteStrategyRegistry() {
        // 内置策略固定装配，不提供插件注册入口
        var builtins = List.of(
                new SmartRouteStrategy(),
                new OrderRouteStrategy(),
                new RotateRouteStrategy(),
                new UsageRouteStrategy(),
                new PaceRouteStrategy(),
                new WeightRouteStrategy(),
                new FreeRouteStrategy(),
                new FastRouteStrategy(),
                new CheapRouteStrategy(),
                new DrainRouteStrategy(),
                new ExpiringRouteStrategy(),
                new ManualRouteStrategy());
        var registered = new EnumMap<AiRouteGroup.RouteMode, RouteStrategy>(AiRouteGroup.RouteMode.class);
        for (var strategy : builtins) {
            registered.put(strategy.mode(), strategy);
        }
        this.strategies = Map.copyOf(registered);
    }

    public RouteStrategy strategyFor(AiRouteGroup.RouteMode mode) {
        var key = mode == null ? AiRouteGroup.RouteMode.SMART : mode;
        return strategies.getOrDefault(key, strategies.get(AiRouteGroup.RouteMode.SMART));
    }
}
