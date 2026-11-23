package cn.tolim.pudans.core.audit;

import cn.tolim.pudans.contract.audit.dto.AuditActorStat;
import cn.tolim.pudans.contract.audit.dto.AuditDurationBucket;
import cn.tolim.pudans.contract.audit.dto.AuditFlowOverview;
import cn.tolim.pudans.contract.audit.dto.AuditFlowOverviewSummary;
import cn.tolim.pudans.contract.audit.dto.AuditFlowStepBucket;
import cn.tolim.pudans.contract.audit.dto.AuditFlowTimelinePoint;
import cn.tolim.pudans.contract.audit.dto.AuditHeatmapCell;
import cn.tolim.pudans.contract.audit.dto.AuditOperationStat;
import cn.tolim.pudans.contract.audit.dto.AuditOverview;
import cn.tolim.pudans.contract.audit.dto.AuditOverviewSummary;
import cn.tolim.pudans.contract.audit.dto.AuditResultStat;
import cn.tolim.pudans.contract.audit.dto.AuditTimelinePoint;
import cn.tolim.pudans.infra.audit.AuditStore;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuditAnalytics {

    private static final String SUCCESS = "SUCCESS";

    private static final String FAILURE = "FAILURE";

    private static final String DENIED = "DENIED";

    private static final List<String> STANDARD_RESULTS = List.of(SUCCESS, FAILURE, DENIED);

    private static final List<String> DURATION_BUCKETS =
            List.of("under50", "50to199", "200to499", "500to999", "1to3s", "over3s");

    private static final List<String> FLOW_STEP_BUCKETS =
            List.of("1", "2", "3", "4to5", "6to10", "over10");

    private static final int TOP_OPERATION_LIMIT = 8;

    private static final int TOP_ACTOR_LIMIT = 6;

    private static final int WEEKDAYS = 7;

    private static final int HOURS_PER_DAY = 24;

    private final AuditLog auditLog;

    // 使用当前时刻聚合审计概览。
    public Mono<AuditOverview> overview(int days) {
        return overview(days, Instant.now());
    }

    // 并行读取审计聚合与时间序列并组装完整概览。
    Mono<AuditOverview> overview(int days, Instant now) {
        var from = PudansTime.startOfDay(now).minus(Duration.ofDays(days - 1L));
        var aggregates = Mono.zip(
                        auditLog.countByResult(from, now, null),
                        auditLog.countDistinctUids(from, now),
                        auditLog.topOperations(from, now, TOP_OPERATION_LIMIT),
                        auditLog.archiveStats(),
                        auditLog.latencyStats(from, now),
                        auditLog.activityHeatmap(from, now).collectList(),
                        auditLog.durationBuckets(from, now),
                        auditLog.topActors(from, now, TOP_ACTOR_LIMIT).collectList())
                .map(tuple -> new AggregateData(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5(),
                        tuple.getT6(),
                        tuple.getT7(),
                        tuple.getT8()));
        var timeline = Mono.zip(
                        auditLog.countPerDay(from, null, null),
                        auditLog.countPerDay(from, null, SUCCESS),
                        auditLog.countPerDay(from, null, FAILURE),
                        auditLog.countPerDay(from, null, DENIED))
                .map(tuple -> new TimelineData(
                        tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()));
        return Mono.zip(aggregates, timeline)
                .map(tuple -> build(days, now, tuple.getT1(), tuple.getT2()));
    }

    // 使用当前时刻聚合链路概览。
    public Mono<AuditFlowOverview> flowOverview(int days) {
        return flowOverview(days, Instant.now());
    }

    // 并行读取链路聚合与图表数据并组装完整概览。
    Mono<AuditFlowOverview> flowOverview(int days, Instant now) {
        var from = PudansTime.startOfDay(now).minus(Duration.ofDays(days - 1L));
        var summary = Mono.zip(
                        auditLog.flowStats(from, now),
                        auditLog.countDistinctUids(from, now),
                        auditLog.topOperations(from, now, TOP_OPERATION_LIMIT),
                        auditLog.countByResult(from, now, null),
                        auditLog.archiveStats())
                .map(tuple -> new FlowSummaryData(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5()));
        var charts = Mono.zip(
                        auditLog.dailyFlowBuckets(from, now).collectList(),
                        auditLog.flowHeatmap(from, now).collectList(),
                        auditLog.flowStepBuckets(from, now),
                        auditLog.flowDurationBuckets(from, now),
                        auditLog.topActors(from, now, TOP_ACTOR_LIMIT).collectList())
                .map(tuple -> new FlowChartData(
                        tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4(), tuple.getT5()));
        return Mono.zip(summary, charts)
                .map(tuple -> buildFlowOverview(days, now, tuple.getT1(), tuple.getT2()));
    }

    // 把聚合结果转换成稳定的图表数据结构。
    private static AuditOverview build(
            int days, Instant now, AggregateData aggregate, TimelineData timeline) {
        var results = resultCounts(aggregate.results());
        var summary = new AuditOverviewSummary(
                results.values().stream().mapToLong(Long::longValue).sum(),
                count(results, SUCCESS),
                count(results, FAILURE),
                count(results, DENIED),
                aggregate.activeUsers(),
                aggregate.latency().averageMs(),
                aggregate.latency().p95Ms(),
                aggregate.archives().activeCount(),
                aggregate.archives().archivedCount());
        return new AuditOverview(
                days,
                now,
                summary,
                dailyTimeline(days, now, timeline),
                heatmap(aggregate.heatmap()),
                operationStats(aggregate.operations()),
                resultStats(results),
                durationBuckets(aggregate.durations()),
                actorStats(aggregate.actors()));
    }

    // 把链路聚合结果转换成稳定的图表数据结构。
    private static AuditFlowOverview buildFlowOverview(
            int days, Instant now, FlowSummaryData summaryData, FlowChartData charts) {
        var stats = summaryData.stats();
        var summary = new AuditFlowOverviewSummary(
                stats.records(),
                stats.flows(),
                summaryData.activeUsers(),
                stats.failedFlows(),
                summaryData.archives().archivedCount(),
                stats.archivedFlows(),
                stats.averageSteps(),
                stats.averageDurationMs(),
                stats.p95DurationMs());
        return new AuditFlowOverview(
                days,
                now,
                summary,
                flowTimeline(days, now, charts.days()),
                heatmap(charts.heatmap()),
                flowSteps(charts.steps()),
                durationBuckets(charts.durations()),
                operationStats(summaryData.operations()),
                resultStats(summaryData.results()),
                actorStats(charts.actors()));
    }

    // 补齐标准结果并保留额外结果顺序。
    private static Map<String, Long> resultCounts(Map<String, Long> results) {
        var ordered = new LinkedHashMap<String, Long>();
        for (var result : STANDARD_RESULTS) {
            ordered.put(result, 0L);
        }
        ordered.putAll(results);
        return ordered;
    }

    // 转换操作分布并保留查询顺序。
    private static List<AuditOperationStat> operationStats(Map<String, Long> operations) {
        return operations.entrySet().stream()
                .map(entry -> new AuditOperationStat(entry.getKey(), entry.getValue()))
                .toList();
    }

    // 转换结果分布并补齐标准结果。
    private static List<AuditResultStat> resultStats(Map<String, Long> results) {
        return resultCounts(results).entrySet().stream()
                .map(entry -> new AuditResultStat(entry.getKey(), entry.getValue()))
                .toList();
    }

    // 转换耗时分布并补齐固定区间。
    private static List<AuditDurationBucket> durationBuckets(Map<String, Long> durations) {
        return DURATION_BUCKETS.stream()
                .map(bucket -> new AuditDurationBucket(
                        bucket, durations.getOrDefault(bucket, 0L)))
                .toList();
    }

    // 转换活跃用户排行。
    private static List<AuditActorStat> actorStats(List<AuditStore.ActorStat> actors) {
        return actors.stream()
                .map(actor -> new AuditActorStat(
                        actor.uid(),
                        actor.records(),
                        actor.failures(),
                        actor.operations(),
                        actor.averageMs()))
                .toList();
    }

    // 按北京时间补齐连续每日的审计趋势。
    private static List<AuditTimelinePoint> dailyTimeline(
            int days, Instant now, TimelineData timeline) {
        var today = PudansTime.dateOf(now);
        var points = new ArrayList<AuditTimelinePoint>(days);
        for (var offset = days - 1; offset >= 0; offset--) {
            var date = today.minusDays(offset);
            points.add(new AuditTimelinePoint(
                    date.toString(),
                    count(timeline.records(), date),
                    count(timeline.success(), date),
                    count(timeline.failure(), date),
                    count(timeline.denied(), date)));
        }
        return points;
    }

    // 返回包含零值单元的完整星期与小时热力图。
    private static List<AuditHeatmapCell> heatmap(List<AuditStore.HeatmapBucket> buckets) {
        var counts = new long[WEEKDAYS][HOURS_PER_DAY];
        for (var bucket : buckets) {
            if (bucket.weekday() >= 1
                    && bucket.weekday() <= WEEKDAYS
                    && bucket.hour() >= 0
                    && bucket.hour() < HOURS_PER_DAY) {
                counts[bucket.weekday() - 1][bucket.hour()] += bucket.records();
            }
        }
        var cells = new ArrayList<AuditHeatmapCell>(WEEKDAYS * HOURS_PER_DAY);
        for (var weekday = 0; weekday < WEEKDAYS; weekday++) {
            for (var hour = 0; hour < HOURS_PER_DAY; hour++) {
                cells.add(new AuditHeatmapCell(weekday + 1, hour, counts[weekday][hour]));
            }
        }
        return cells;
    }

    // 按北京时间补齐连续每日的链路趋势。
    private static List<AuditFlowTimelinePoint> flowTimeline(
            int days, Instant now, List<AuditStore.FlowDayBucket> buckets) {
        var byDay = new LinkedHashMap<LocalDate, AuditStore.FlowDayBucket>();
        for (var bucket : buckets) {
            byDay.put(bucket.day(), bucket);
        }
        var today = PudansTime.dateOf(now);
        var points = new ArrayList<AuditFlowTimelinePoint>(days);
        for (var offset = days - 1; offset >= 0; offset--) {
            var date = today.minusDays(offset);
            points.add(flowPoint(date, byDay.get(date)));
        }
        return points;
    }

    // 转换单个每日链路计数，缺失日期按零处理。
    private static AuditFlowTimelinePoint flowPoint(
            LocalDate date, AuditStore.FlowDayBucket bucket) {
        return new AuditFlowTimelinePoint(
                date.toString(),
                bucket == null ? 0 : bucket.flows(),
                bucket == null ? 0 : bucket.records(),
                bucket == null ? 0 : bucket.failedFlows(),
                bucket == null ? 0 : bucket.archivedRecords());
    }

    // 补齐固定步骤区间并保持展示顺序。
    private static List<AuditFlowStepBucket> flowSteps(Map<String, Long> steps) {
        return FLOW_STEP_BUCKETS.stream()
                .map(bucket -> new AuditFlowStepBucket(bucket, steps.getOrDefault(bucket, 0L)))
                .toList();
    }

    // 读取每日计数，缺失日期按零处理。
    private static long count(Map<LocalDate, Long> counts, LocalDate date) {
        return counts.getOrDefault(date, 0L);
    }

    // 读取指定结果的计数，缺失结果按零处理。
    private static long count(Map<String, Long> counts, String result) {
        return counts.getOrDefault(result, 0L);
    }

    private record AggregateData(
            Map<String, Long> results,
            long activeUsers,
            Map<String, Long> operations,
            AuditStore.ArchiveStats archives,
            AuditStore.LatencyStats latency,
            List<AuditStore.HeatmapBucket> heatmap,
            Map<String, Long> durations,
            List<AuditStore.ActorStat> actors) {}

    private record TimelineData(
            Map<LocalDate, Long> records,
            Map<LocalDate, Long> success,
            Map<LocalDate, Long> failure,
            Map<LocalDate, Long> denied) {}

    private record FlowSummaryData(
            AuditStore.FlowStats stats,
            long activeUsers,
            Map<String, Long> operations,
            Map<String, Long> results,
            AuditStore.ArchiveStats archives) {}

    private record FlowChartData(
            List<AuditStore.FlowDayBucket> days,
            List<AuditStore.HeatmapBucket> heatmap,
            Map<String, Long> steps,
            Map<String, Long> durations,
            List<AuditStore.ActorStat> actors) {}
}
