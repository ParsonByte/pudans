package cn.tolim.pudans.core.ai.routing.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import cn.tolim.pudans.core.ai.routing.RouteSeats;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteStrategiesTest {

    private final RouteStrategyRegistry registry = new RouteStrategyRegistry();

    @Test
    void orderKeepsInputOrder() {
        var seats = List.of(
                RouteSeats.seat("p", "a1", "m1", AiModel.Tier.MEDIUM),
                RouteSeats.seat("p", "a2", "m2", AiModel.Tier.MEDIUM),
                RouteSeats.seat("p", "a3", "m3", AiModel.Tier.MEDIUM));

        assertThat(models(sort(AiRouteGroup.RouteMode.ORDER, seats))).containsExactly("m1", "m2", "m3");
    }

    @Test
    void smartPrefersEarliestResetWithQuotaData() {
        var first = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.HIGH)
                .quotaLimit(100L)
                .quotaUsed(10L)
                .resetsAt(Instant.parse("2026-11-20T00:00:00Z"))
                .build();
        var second = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.HIGH)
                .quotaLimit(100L)
                .quotaUsed(10L)
                .resetsAt(Instant.parse("2026-11-21T00:00:00Z"))
                .build();

        assertThat(models(sort(AiRouteGroup.RouteMode.SMART, List.of(second, first))))
                .containsExactly("m1", "m2");
    }

    @Test
    void smartPrefersMoreRemainingQuotaWhenResetMatches() {
        var resetAt = Instant.parse("2026-11-20T00:00:00Z");
        var low = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.HIGH)
                .quotaLimit(100L)
                .quotaUsed(90L)
                .resetsAt(resetAt)
                .build();
        var high = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.HIGH)
                .quotaLimit(100L)
                .quotaUsed(10L)
                .resetsAt(resetAt)
                .build();

        assertThat(models(sort(AiRouteGroup.RouteMode.SMART, List.of(low, high))))
                .containsExactly("m2", "m1");
    }

    @Test
    void smartFallsBackToCostAndHealthWithoutQuotaData() {
        var healthyExpensive = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.HIGH)
                .cost(BigDecimal.valueOf(5))
                .healthScore(100)
                .build();
        var unhealthyCheap = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.HIGH)
                .cost(BigDecimal.ONE)
                .healthScore(80)
                .build();
        var healthyCheap = RouteSeat.builder()
                .provider("p")
                .account("a3")
                .model("m3")
                .tier(AiModel.Tier.HIGH)
                .cost(BigDecimal.ONE)
                .healthScore(95)
                .build();

        assertThat(models(sort(AiRouteGroup.RouteMode.SMART, List.of(healthyExpensive, unhealthyCheap, healthyCheap))))
                .containsExactly("m3", "m2", "m1");
    }

    @Test
    void rotateCyclesThroughSeats() {
        var seats = List.of(
                RouteSeats.seat("p", "a1", "m1", AiModel.Tier.MEDIUM),
                RouteSeats.seat("p", "a2", "m2", AiModel.Tier.MEDIUM),
                RouteSeats.seat("p", "a3", "m3", AiModel.Tier.MEDIUM));

        assertThat(models(sort(AiRouteGroup.RouteMode.ROTATE, seats))).containsExactly("m1", "m2", "m3");
        assertThat(models(sort(AiRouteGroup.RouteMode.ROTATE, seats))).containsExactly("m2", "m3", "m1");
        assertThat(models(sort(AiRouteGroup.RouteMode.ROTATE, seats))).containsExactly("m3", "m1", "m2");
        assertThat(models(sort(AiRouteGroup.RouteMode.ROTATE, seats))).containsExactly("m1", "m2", "m3");
    }

    @Test
    void usagePrefersFewestTokens() {
        var heavy = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.MEDIUM)
                .tokensUsed(300)
                .build();
        var light = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.MEDIUM)
                .tokensUsed(100)
                .build();
        var middle = RouteSeat.builder()
                .provider("p")
                .account("a3")
                .model("m3")
                .tier(AiModel.Tier.MEDIUM)
                .tokensUsed(200)
                .build();

        assertThat(models(sort(AiRouteGroup.RouteMode.USAGE, List.of(heavy, light, middle))))
                .containsExactly("m2", "m3", "m1");
    }

    @Test
    void pacePrefersHighestRemainingRate() {
        var slow = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.MEDIUM)
                .pace(10)
                .build();
        var fast = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.MEDIUM)
                .pace(50)
                .build();
        var middle = RouteSeat.builder()
                .provider("p")
                .account("a3")
                .model("m3")
                .tier(AiModel.Tier.MEDIUM)
                .pace(20)
                .build();

        assertThat(models(sort(AiRouteGroup.RouteMode.PACE, List.of(slow, fast, middle))))
                .containsExactly("m2", "m3", "m1");
    }

    @Test
    void weightDistributesBySmoothWeightedRoundRobin() {
        var heavy = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.MEDIUM)
                .weight(3)
                .build();
        var light = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.MEDIUM)
                .weight(1)
                .build();
        var strategy = new WeightRouteStrategy();
        var context = new RouteStrategyContext(null);
        var selected = new ArrayList<String>();
        for (int index = 0; index < 4; index++) {
            selected.add(strategy.sort(List.of(heavy, light), context).get(0).getModel());
        }

        assertThat(selected).containsExactly("m1", "m1", "m2", "m1");
    }

    @Test
    void freePrefersFreeModelThenFreePlan() {
        var freeModel = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.CHEAP)
                .free(true)
                .cost(BigDecimal.valueOf(5))
                .build();
        var freePlan = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.CHEAP)
                .freePlan(true)
                .cost(BigDecimal.ONE)
                .build();
        var paid = RouteSeat.builder()
                .provider("p")
                .account("a3")
                .model("m3")
                .tier(AiModel.Tier.CHEAP)
                .cost(BigDecimal.ZERO)
                .build();

        assertThat(models(sort(AiRouteGroup.RouteMode.FREE, List.of(paid, freePlan, freeModel))))
                .containsExactly("m1", "m2", "m3");
    }

    @Test
    void fastPrefersProbeThenLowestTtftThenHighestTps() {
        var unknown = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.MEDIUM)
                .ttftMillis(0)
                .tps(0)
                .build();
        var fast = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.MEDIUM)
                .ttftMillis(200)
                .tps(20)
                .build();
        var slower = RouteSeat.builder()
                .provider("p")
                .account("a3")
                .model("m3")
                .tier(AiModel.Tier.MEDIUM)
                .ttftMillis(200)
                .tps(5)
                .build();
        var slow = RouteSeat.builder()
                .provider("p")
                .account("a4")
                .model("m4")
                .tier(AiModel.Tier.MEDIUM)
                .ttftMillis(500)
                .tps(100)
                .build();

        assertThat(models(sort(AiRouteGroup.RouteMode.FAST, List.of(slow, slower, unknown, fast))))
                .containsExactly("m1", "m2", "m3", "m4");
    }

    @Test
    void cheapPrefersLowestCostAndPutsUnknownCostLast() {
        var expensive = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.CHEAP)
                .cost(BigDecimal.valueOf(3))
                .build();
        var cheap = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.CHEAP)
                .cost(BigDecimal.ONE)
                .build();
        var unknown = RouteSeats.seat("p", "a3", "m3", AiModel.Tier.CHEAP);

        assertThat(models(sort(AiRouteGroup.RouteMode.CHEAP, List.of(expensive, unknown, cheap))))
                .containsExactly("m2", "m1", "m3");
    }

    @Test
    void drainSticksToCurrentAccountAndNeverReturns() {
        var first = RouteSeats.seat("p", "a1", "m1", AiModel.Tier.HIGH);
        var second = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var strategy = new DrainRouteStrategy();
        var context = new RouteStrategyContext(null);

        assertThat(models(strategy.sort(List.of(first, second), context))).containsExactly("m1", "m2");
        assertThat(models(strategy.sort(List.of(first, second), context))).containsExactly("m1", "m2");
        assertThat(models(strategy.sort(List.of(second), context))).containsExactly("m2");
        assertThat(models(strategy.sort(List.of(first, second), context))).containsExactly("m2", "m1");
    }

    @Test
    void drainSkipsSpentSeats() {
        var spent = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.HIGH)
                .spent(true)
                .build();
        var available = RouteSeats.seat("p", "a2", "m2", AiModel.Tier.HIGH);
        var strategy = new DrainRouteStrategy();

        assertThat(models(strategy.sort(List.of(spent, available), new RouteStrategyContext(null))))
                .containsExactly("m2", "m1");
    }

    @Test
    void expiringPrefersEarliestReset() {
        var late = RouteSeat.builder()
                .provider("p")
                .account("a1")
                .model("m1")
                .tier(AiModel.Tier.CHEAP)
                .resetsAt(Instant.parse("2026-11-22T00:00:00Z"))
                .build();
        var early = RouteSeat.builder()
                .provider("p")
                .account("a2")
                .model("m2")
                .tier(AiModel.Tier.CHEAP)
                .resetsAt(Instant.parse("2026-11-20T00:00:00Z"))
                .build();
        var never = RouteSeats.seat("p", "a3", "m3", AiModel.Tier.CHEAP);

        assertThat(models(sort(AiRouteGroup.RouteMode.EXPIRING, List.of(late, never, early))))
                .containsExactly("m2", "m1", "m3");
    }

    @Test
    void manualPrefersTheConfiguredPick() {
        var seats = List.of(
                RouteSeats.seat("p", "a1", "m1", AiModel.Tier.MEDIUM),
                RouteSeats.seat("p", "a2", "m2", AiModel.Tier.MEDIUM),
                RouteSeats.seat("p", "a3", "m3", AiModel.Tier.MEDIUM));
        var strategy = new ManualRouteStrategy();

        assertThat(models(strategy.sort(seats, new RouteStrategyContext("p/m2"))))
                .containsExactly("m2", "m1", "m3");
        assertThat(models(strategy.sort(seats, new RouteStrategyContext("m3"))))
                .containsExactly("m3", "m1", "m2");
        assertThat(models(strategy.sort(seats, new RouteStrategyContext("unknown"))))
                .containsExactly("m1", "m2", "m3");
    }

    private List<RouteSeat> sort(AiRouteGroup.RouteMode mode, List<RouteSeat> seats) {
        return registry.strategyFor(mode).sort(seats, new RouteStrategyContext(null));
    }

    private static List<String> models(List<RouteSeat> seats) {
        return seats.stream().map(RouteSeat::getModel).toList();
    }
}
