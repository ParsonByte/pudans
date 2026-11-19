package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WeightRouteStrategy implements RouteStrategy {

    private final ConcurrentMap<String, WeightState> states = new ConcurrentHashMap<>();

    @Override
    public AiRouteGroup.RouteMode mode() {
        return AiRouteGroup.RouteMode.WEIGHT;
    }

    @Override
    public List<RouteSeat> sort(List<RouteSeat> seats, RouteStrategyContext context) {
        var ordered = new ArrayList<>(seats);
        if (ordered.size() < 2) {
            return ordered;
        }
        var state = states.computeIfAbsent(fingerprint(ordered), ignored -> new WeightState(ordered.size()));
        synchronized (state) {
            int total = 0;
            for (int index = 0; index < ordered.size(); index++) {
                int weight = Math.max(1, ordered.get(index).getWeight());
                state.current[index] += weight;
                total += weight;
            }
            int selected = 0;
            for (int index = 1; index < state.current.length; index++) {
                if (state.current[index] > state.current[selected]) {
                    selected = index;
                }
            }
            state.current[selected] -= total;
            ordered.add(0, ordered.remove(selected));
        }
        return ordered;
    }

    private static String fingerprint(List<RouteSeat> seats) {
        var builder = new StringBuilder();
        for (var seat : seats) {
            builder.append(seat.key())
                    .append('|')
                    .append(Math.max(1, seat.getWeight()))
                    .append('\n');
        }
        return builder.toString();
    }

    private static final class WeightState {

        private final int[] current;

        private WeightState(int size) {
            this.current = new int[size];
        }
    }
}
