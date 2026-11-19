package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.List;

public interface RouteStrategy {

    AiRouteGroup.RouteMode mode();

    List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context);
}
