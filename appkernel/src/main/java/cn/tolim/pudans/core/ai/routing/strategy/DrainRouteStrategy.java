package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class DrainRouteStrategy implements RouteStrategy {

    private final Set<String> drained = ConcurrentHashMap.newKeySet();

    private final AtomicReference<String> current = new AtomicReference<>();

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.DRAIN;
    }

    @Override
    public synchronized List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        var available = ordered.stream().filter(seat -> !seat.isSpent()).toList();
        if (available.isEmpty()) {
            return ordered;
        }
        var currentKey = current.get();
        var active = available.stream().filter(seat -> seat.key().equals(currentKey)).findFirst();
        if (active.isEmpty()) {
            if (currentKey != null) {
                drained.add(currentKey);
            }
            active = available.stream().filter(seat -> !drained.contains(seat.key())).findFirst();
            if (active.isEmpty()) {
                drained.clear();
                active = available.stream().findFirst();
            }
            var next = active.orElseThrow();
            drained.add(next.key());
            current.set(next.key());
        }
        var selected = active.orElseThrow();
        ordered.remove(selected);
        ordered.add(0, selected);
        return ordered;
    }
}
