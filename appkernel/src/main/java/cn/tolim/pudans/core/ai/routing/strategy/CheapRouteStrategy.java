package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.List;

public class CheapRouteStrategy implements RouteStrategy {

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.CHEAP;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        ordered.sort(RouteSeatComparators.costAscending());
        return ordered;
    }
}
