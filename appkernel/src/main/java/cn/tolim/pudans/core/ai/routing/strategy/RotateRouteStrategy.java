package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class RotateRouteStrategy implements RouteStrategy {

    private final AtomicLong cursor = new AtomicLong();

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.ROTATE;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        if (ordered.size() < 2) {
            return ordered;
        }
        int offset = (int) Math.floorMod(cursor.getAndIncrement(), ordered.size());
        Collections.rotate(ordered, -offset);
        return ordered;
    }
}
