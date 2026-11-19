package cn.tolim.pudans.core.ai.routing.strategy;

import cn.tolim.pudans.core.ai.routing.RouteSeat;
import java.util.Comparator;

final class RouteSeatComparators {

    private RouteSeatComparators() {
    }

    static Comparator<RouteSeat> costAscending() {
        return Comparator.comparing(RouteSeat::getCost, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    static Comparator<RouteSeat> healthDescending() {
        return Comparator.comparingDouble(RouteSeat::getHealthScore).reversed();
    }

    static Comparator<RouteSeat> ttftAscending() {
        return Comparator.comparingLong(RouteSeat::getTtftMillis);
    }

    static Comparator<RouteSeat> resetsAtAscending() {
        return Comparator.comparing(RouteSeat::getResetsAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
