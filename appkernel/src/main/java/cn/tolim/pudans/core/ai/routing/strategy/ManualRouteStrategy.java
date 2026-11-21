package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.ai.resource.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ManualRouteStrategy implements RouteStrategy {

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.MANUAL;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        var pick = context == null ? null : context.manualPick();
        if (pick == null || pick.isBlank()) {
            return ordered;
        }
        ordered.sort(Comparator.comparing((RouteSeat seat) -> !seat.matches(pick)));
        return ordered;
    }
}
