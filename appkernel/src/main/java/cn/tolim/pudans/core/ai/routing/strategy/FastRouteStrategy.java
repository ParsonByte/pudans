package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FastRouteStrategy implements RouteStrategy {

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.FAST;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        ordered.sort(Comparator.comparingLong(FastRouteStrategy::effectiveTtft)
                .thenComparing(Comparator.comparingDouble(RouteSeat::getTps).reversed()));
        return ordered;
    }

    private static long effectiveTtft(RouteSeat seat) {
        // 无延迟数据时按 0 处理，给候选一次探测机会
        return seat.getTtftMillis() <= 0 ? 0 : seat.getTtftMillis();
    }
}
