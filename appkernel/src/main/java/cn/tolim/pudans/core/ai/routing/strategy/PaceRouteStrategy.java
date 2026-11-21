package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.ai.resource.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PaceRouteStrategy implements RouteStrategy {

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.PACE;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        ordered.sort(Comparator.comparingDouble(RouteSeat::getPace)
                .reversed()
                .thenComparing(RouteSeatComparators.resetsAtAscending()));
        return ordered;
    }
}
