package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.List;

public class SmartRouteStrategy implements RouteStrategy {

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.SMART;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        if (ordered.stream().noneMatch(RouteSeat::hasQuotaData)) {
            // 无配额数据时降级为价格、健康度和延迟排序
            ordered.sort(RouteSeatComparators.costAscending()
                    .thenComparing(RouteSeatComparators.healthDescending())
                    .thenComparing(RouteSeatComparators.ttftAscending()));
            return ordered;
        }
        ordered.sort(SmartRouteStrategy::compare);
        return ordered;
    }

    private static int compare(RouteSeat left, RouteSeat right) {
        int quotaOrder = Boolean.compare(right.hasQuotaData(), left.hasQuotaData());
        if (quotaOrder != 0) {
            return quotaOrder;
        }
        int resetOrder = RouteSeatComparators.resetsAtAscending().compare(left, right);
        if (resetOrder != 0) {
            return resetOrder;
        }
        int remainingOrder = Long.compare(right.remainingQuota(), left.remainingQuota());
        if (remainingOrder != 0) {
            return remainingOrder;
        }
        int healthOrder = Double.compare(right.getHealthScore(), left.getHealthScore());
        if (healthOrder != 0) {
            return healthOrder;
        }
        return Long.compare(left.getTtftMillis(), right.getTtftMillis());
    }
}
