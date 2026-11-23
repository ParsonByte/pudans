package cn.tolim.pudans.core.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.audit.dto.AuditDurationBucket;
import cn.tolim.pudans.contract.audit.dto.AuditFlowOverviewSummary;
import cn.tolim.pudans.contract.audit.dto.AuditFlowStepBucket;
import cn.tolim.pudans.contract.audit.dto.AuditFlowTimelinePoint;
import cn.tolim.pudans.contract.audit.dto.AuditOperationStat;
import cn.tolim.pudans.contract.audit.dto.AuditOverviewSummary;
import cn.tolim.pudans.contract.audit.dto.AuditResultStat;
import cn.tolim.pudans.contract.audit.dto.AuditTimelinePoint;
import cn.tolim.pudans.infra.audit.AuditStore;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AuditAnalyticsTest {

    private static final Instant NOW = Instant.parse("2026-11-23T04:00:00Z");

    private static final Instant FROM = Instant.parse("2026-11-16T16:00:00Z");

    @Mock
    AuditLog auditLog;

    private AuditAnalytics analytics;

    @BeforeEach
    void setUp() {
        analytics = new AuditAnalytics(auditLog);
    }

    @Test
    void overviewBuildsCompleteChartsAndStableOrders() {
        var results = new LinkedHashMap<String, Long>();
        results.put("SUCCESS", 3L);
        results.put("FAILURE", 3L);
        results.put("EXTRA", 2L);
        var durations = new LinkedHashMap<String, Long>();
        durations.put("over3s", 3L);
        durations.put("under50", 5L);
        when(auditLog.countByResult(FROM, NOW, null)).thenReturn(Mono.just(results));
        when(auditLog.countDistinctUids(FROM, NOW)).thenReturn(Mono.just(4L));
        var operations = new LinkedHashMap<String, Long>();
        operations.put("READ", 5L);
        operations.put("LOGIN", 3L);
        when(auditLog.topOperations(FROM, NOW, 8)).thenReturn(Mono.just(operations));
        when(auditLog.archiveStats()).thenReturn(Mono.just(new AuditStore.ArchiveStats(100L, 50L)));
        when(auditLog.latencyStats(FROM, NOW)).thenReturn(Mono.just(new AuditStore.LatencyStats(42L, 95L)));
        when(auditLog.activityHeatmap(FROM, NOW))
                .thenReturn(Flux.just(
                        new AuditStore.HeatmapBucket(3, 11, 5),
                        new AuditStore.HeatmapBucket(6, 0, 3)));
        when(auditLog.durationBuckets(FROM, NOW)).thenReturn(Mono.just(durations));
        when(auditLog.topActors(FROM, NOW, 6))
                .thenReturn(Flux.just(
                        new AuditStore.ActorStat("uid-1", 5, 2, 2, 35),
                        new AuditStore.ActorStat("uid-2", 3, 0, 1, 10)));
        when(auditLog.countPerDay(any(Instant.class), isNull(), isNull()))
                .thenReturn(Mono.just(Map.of(
                        LocalDate.parse("2026-11-18"), 6L,
                        LocalDate.parse("2026-11-23"), 2L)));
        when(auditLog.countPerDay(any(Instant.class), isNull(), eq("SUCCESS")))
                .thenReturn(Mono.just(Map.of(
                        LocalDate.parse("2026-11-18"), 3L)));
        when(auditLog.countPerDay(any(Instant.class), isNull(), eq("FAILURE")))
                .thenReturn(Mono.just(Map.of(LocalDate.parse("2026-11-18"), 2L)));
        when(auditLog.countPerDay(any(Instant.class), isNull(), eq("DENIED")))
                .thenReturn(Mono.just(Map.of()));

        var overview = analytics.overview(7, NOW).block();

        assertThat(overview).isNotNull();
        assertThat(overview.rangeDays()).isEqualTo(7);
        assertThat(overview.generatedAt()).isEqualTo(NOW);
        assertThat(overview.summary())
                .isEqualTo(new AuditOverviewSummary(8, 3, 3, 0, 4, 42, 95, 100, 50));
        assertThat(overview.results()).containsExactly(
                new AuditResultStat("SUCCESS", 3),
                new AuditResultStat("FAILURE", 3),
                new AuditResultStat("DENIED", 0),
                new AuditResultStat("EXTRA", 2));
        assertThat(overview.durations()).containsExactly(
                new AuditDurationBucket("under50", 5),
                new AuditDurationBucket("50to199", 0),
                new AuditDurationBucket("200to499", 0),
                new AuditDurationBucket("500to999", 0),
                new AuditDurationBucket("1to3s", 0),
                new AuditDurationBucket("over3s", 3));
        assertThat(overview.actors()).hasSize(2);
        assertThat(overview.actors().getFirst().averageDurationMs()).isEqualTo(35);
        assertThat(overview.timeline()).hasSize(7);
        assertThat(overview.timeline()).anySatisfy(point -> {
            if ("2026-11-18".equals(point.date())) {
                assertThat(point).isEqualTo(new AuditTimelinePoint("2026-11-18", 6, 3, 2, 0));
            }
        });
        assertThat(overview.timeline()).anySatisfy(point -> {
            if ("2026-11-23".equals(point.date())) {
                assertThat(point).isEqualTo(new AuditTimelinePoint("2026-11-23", 2, 0, 0, 0));
            }
        });
        assertThat(overview.heatmap()).hasSize(168);
        assertThat(overview.heatmap().stream().mapToLong(cell -> cell.records()).sum())
                .isEqualTo(8);
        assertThat(overview.heatmap()).anySatisfy(cell -> {
            if (cell.weekday() == 3 && cell.hour() == 11) {
                assertThat(cell.records()).isEqualTo(5);
            }
        });

        verify(auditLog).countByResult(FROM, NOW, null);
        verify(auditLog).topOperations(FROM, NOW, 8);
        verify(auditLog).topActors(FROM, NOW, 6);
    }

    @Test
    void flowOverviewBuildsCompleteFlowChartsAndRetentionSummary() {
        var results = new LinkedHashMap<String, Long>();
        results.put("SUCCESS", 8L);
        results.put("FAILURE", 3L);
        results.put("DENIED", 1L);
        var operations = new LinkedHashMap<String, Long>();
        operations.put("READ", 7L);
        operations.put("LOGIN", 5L);
        when(auditLog.flowStats(FROM, NOW))
                .thenReturn(Mono.just(new AuditStore.FlowStats(12L, 4L, 2L, 1L, 3.0, 1200L, 3000L)));
        when(auditLog.countDistinctUids(FROM, NOW)).thenReturn(Mono.just(5L));
        when(auditLog.topOperations(FROM, NOW, 8)).thenReturn(Mono.just(operations));
        when(auditLog.countByResult(FROM, NOW, null)).thenReturn(Mono.just(results));
        when(auditLog.archiveStats()).thenReturn(Mono.just(new AuditStore.ArchiveStats(50L, 200L)));
        when(auditLog.dailyFlowBuckets(FROM, NOW))
                .thenReturn(Flux.just(
                        new AuditStore.FlowDayBucket(LocalDate.parse("2026-11-17"), 1L, 4L, 1L, 0L),
                        new AuditStore.FlowDayBucket(LocalDate.parse("2026-11-18"), 2L, 5L, 0L, 2L),
                        new AuditStore.FlowDayBucket(LocalDate.parse("2026-11-23"), 1L, 3L, 1L, 1L)));
        when(auditLog.flowHeatmap(FROM, NOW))
                .thenReturn(Flux.just(
                        new AuditStore.HeatmapBucket(3, 11, 2),
                        new AuditStore.HeatmapBucket(6, 0, 1),
                        new AuditStore.HeatmapBucket(7, 23, 1)));
        var steps = new LinkedHashMap<String, Long>();
        steps.put("over10", 1L);
        steps.put("4to5", 2L);
        steps.put("1", 1L);
        when(auditLog.flowStepBuckets(FROM, NOW)).thenReturn(Mono.just(steps));
        var durations = new LinkedHashMap<String, Long>();
        durations.put("over3s", 3L);
        durations.put("under50", 1L);
        when(auditLog.flowDurationBuckets(FROM, NOW)).thenReturn(Mono.just(durations));
        when(auditLog.topActors(FROM, NOW, 6))
                .thenReturn(Flux.just(new AuditStore.ActorStat("uid-1", 9, 2, 3, 40)));

        var overview = analytics.flowOverview(7, NOW).block();

        assertThat(overview).isNotNull();
        assertThat(overview.rangeDays()).isEqualTo(7);
        assertThat(overview.generatedAt()).isEqualTo(NOW);
        assertThat(overview.summary())
                .isEqualTo(new AuditFlowOverviewSummary(12, 4, 5, 2, 200, 1, 3.0, 1200, 3000));
        assertThat(overview.timeline()).hasSize(7);
        assertThat(overview.timeline().getFirst())
                .isEqualTo(new AuditFlowTimelinePoint("2026-11-17", 1, 4, 1, 0));
        assertThat(overview.timeline()).anySatisfy(point -> {
            if ("2026-11-18".equals(point.date())) {
                assertThat(point).isEqualTo(new AuditFlowTimelinePoint("2026-11-18", 2, 5, 0, 2));
            }
        });
        assertThat(overview.timeline()).anySatisfy(point -> {
            if ("2026-11-19".equals(point.date())) {
                assertThat(point).isEqualTo(new AuditFlowTimelinePoint("2026-11-19", 0, 0, 0, 0));
            }
        });
        assertThat(overview.heatmap()).hasSize(168);
        assertThat(overview.heatmap().stream().mapToLong(cell -> cell.records()).sum())
                .isEqualTo(4);
        assertThat(overview.steps()).containsExactly(
                new AuditFlowStepBucket("1", 1),
                new AuditFlowStepBucket("2", 0),
                new AuditFlowStepBucket("3", 0),
                new AuditFlowStepBucket("4to5", 2),
                new AuditFlowStepBucket("6to10", 0),
                new AuditFlowStepBucket("over10", 1));
        assertThat(overview.durations()).containsExactly(
                new AuditDurationBucket("under50", 1),
                new AuditDurationBucket("50to199", 0),
                new AuditDurationBucket("200to499", 0),
                new AuditDurationBucket("500to999", 0),
                new AuditDurationBucket("1to3s", 0),
                new AuditDurationBucket("over3s", 3));
        assertThat(overview.results()).containsExactly(
                new AuditResultStat("SUCCESS", 8),
                new AuditResultStat("FAILURE", 3),
                new AuditResultStat("DENIED", 1));
        assertThat(overview.operations()).containsExactly(
                new AuditOperationStat("READ", 7),
                new AuditOperationStat("LOGIN", 5));
        assertThat(overview.actors()).hasSize(1);

        verify(auditLog).flowStats(FROM, NOW);
        verify(auditLog).flowHeatmap(FROM, NOW);
        verify(auditLog).flowStepBuckets(FROM, NOW);
        verify(auditLog).flowDurationBuckets(FROM, NOW);
        verify(auditLog).topOperations(FROM, NOW, 8);
        verify(auditLog).topActors(FROM, NOW, 6);
    }
}
