package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class UsageRouteStrategy implements RouteStrategy {

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.USAGE;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        ordered.sort(Comparator.comparingLong(RouteSeat::getTokensUsed));
        return ordered;
    }
}
