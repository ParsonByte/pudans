package cn.tolim.pudans.core.risk;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.contract.risk.dto.RiskEventView;
import cn.tolim.pudans.contract.risk.dto.RiskHeatmapCell;
import cn.tolim.pudans.contract.risk.dto.RiskOverview;
import cn.tolim.pudans.contract.risk.dto.RiskOverviewSummary;
import cn.tolim.pudans.contract.risk.dto.RiskRelation;
import cn.tolim.pudans.contract.risk.dto.RiskSceneStat;
import cn.tolim.pudans.contract.risk.dto.RiskSubjectStat;
import cn.tolim.pudans.contract.risk.dto.RiskTimelinePoint;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.audit.AuditStore;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RiskAnalytics {

    private static final String BLOCKED_OPERATION = "RISK_BLOCKED";

    private static final String ALERT_OPERATION = "RISK_ALERT";

    private static final String UNKNOWN = "unknown";

    private static final int EVENT_SCAN_LIMIT = 20_000;

    private static final int TOP_SUBJECT_LIMIT = 8;

    private static final int RELATION_LIMIT = 80;

    private static final int RECENT_EVENT_LIMIT = 12;

    private static final int WEEKDAYS = 7;

    private static final int HOURS_PER_DAY = 24;

    private final AuditLog auditLog;

    private final RiskGuard riskGuard;

    private final RiskRuleRegistry ruleRegistry;

    // 使用当前时刻聚合风险态势。
    public Mono<RiskOverview> overview(int days) {
        return overview(days, Instant.now());
    }

    // 读取受限数量的风险审计并生成完整图表数据。
    Mono<RiskOverview> overview(int days, Instant now) {
        var from = PudansTime.startOfDay(now).minus(Duration.ofDays(days - 1L));
        var blocked = auditLog
                .query(new AuditStore.AuditQuery(
                        null, from, now, null, BLOCKED_OPERATION, null, null, null, EVENT_SCAN_LIMIT, false))
                .collectList()
                .defaultIfEmpty(List.of());
        var alerts = auditLog
                .query(new AuditStore.AuditQuery(
                        null, from, now, null, ALERT_OPERATION, null, null, null, EVENT_SCAN_LIMIT, false))
                .collectList()
                .defaultIfEmpty(List.of());
        var bans = riskGuard.listBans().collectList().defaultIfEmpty(List.of());
        return Mono.zip(blocked, alerts, bans)
                .map(tuple -> build(days, now, tuple.getT1(), tuple.getT2(), tuple.getT3()));
    }

    // 汇总事件、场景、主体、关系与时间分布。
    private RiskOverview build(
            int days, Instant now, List<AuditRecord> blocked, List<AuditRecord> alerts, List<RiskGuard.RiskBan> bans) {
        var records = Stream.concat(blocked.stream(), alerts.stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        RiskAnalytics::auditId,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values()
                .stream()
                .sorted(Comparator.comparing(
                        RiskAnalytics::occurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        var rules = ruleRegistry.rules();
        var summary = new RiskOverviewSummary(
                records.size(),
                records.stream().filter(record -> BLOCKED_OPERATION.equals(record.getOperation())).count(),
                records.stream().filter(record -> ALERT_OPERATION.equals(record.getOperation())).count(),
                bans.size(),
                records.stream().map(RiskAnalytics::subjectOf).collect(Collectors.toSet()).size(),
                rules.values().stream().filter(RiskRule.Spec::isEnabled).count(),
                rules.size());
        return new RiskOverview(
                days,
                now,
                blocked.size() >= EVENT_SCAN_LIMIT || alerts.size() >= EVENT_SCAN_LIMIT,
                summary,
                timeline(days, now, records),
                heatmap(records),
                sceneStats(records, rules),
                subjectStats(records),
                relations(records),
                records.stream().limit(RECENT_EVENT_LIMIT).map(RiskAnalytics::eventView).toList());
    }

    // 按北京时间补齐连续每日的风险事件序列。
    private static List<RiskTimelinePoint> timeline(int days, Instant now, List<AuditRecord> records) {
        var events = countByDate(records, null);
        var blocked = countByDate(records, BLOCKED_OPERATION);
        var alerts = countByDate(records, ALERT_OPERATION);
        var today = PudansTime.dateOf(now);
        var points = new ArrayList<RiskTimelinePoint>(days);
        for (var offset = days - 1; offset >= 0; offset--) {
            var date = today.minusDays(offset);
            points.add(new RiskTimelinePoint(
                    date.toString(),
                    events.getOrDefault(date, 0L),
                    blocked.getOrDefault(date, 0L),
                    alerts.getOrDefault(date, 0L)));
        }
        return points;
    }

    // 统计指定操作的风险事件数量。
    private static Map<LocalDate, Long> countByDate(List<AuditRecord> records, String operation) {
        return records.stream()
                .filter(record -> record.getTimestamp() != null)
                .filter(record -> operation == null || operation.equals(record.getOperation()))
                .collect(Collectors.groupingBy(
                        record -> PudansTime.dateOf(record.getTimestamp()),
                        Collectors.counting()));
    }

    // 返回包含零值单元的完整星期与小时热力图。
    private static List<RiskHeatmapCell> heatmap(List<AuditRecord> records) {
        var counts = new long[WEEKDAYS][HOURS_PER_DAY];
        for (var record : records) {
            if (record.getTimestamp() == null) {
                continue;
            }
            var time = ZonedDateTime.ofInstant(record.getTimestamp(), PudansTime.BEIJING);
            counts[time.getDayOfWeek().getValue() - 1][time.getHour()]++;
        }
        var cells = new ArrayList<RiskHeatmapCell>(WEEKDAYS * HOURS_PER_DAY);
        for (var weekday = 0; weekday < WEEKDAYS; weekday++) {
            for (var hour = 0; hour < HOURS_PER_DAY; hour++) {
                cells.add(new RiskHeatmapCell(weekday + 1, hour, counts[weekday][hour]));
            }
        }
        return cells;
    }

    // 合并已配置规则与审计中出现的场景。
    private List<RiskSceneStat> sceneStats(List<AuditRecord> records, Map<String, RiskRule.Spec> rules) {
        var recordsByScene = records.stream()
                .collect(Collectors.groupingBy(RiskAnalytics::sceneOf));
        var scenes = new TreeSet<>(rules.keySet());
        scenes.addAll(recordsByScene.keySet());
        return scenes.stream()
                .map(scene -> {
                    var rule = rules.get(scene);
                    var sceneRecords = recordsByScene.getOrDefault(scene, List.of());
                    return new RiskSceneStat(
                            scene,
                            displayLabel(scene, rule),
                            rule == null ? null : rule.getDescription(),
                            rule == null ? null : rule.getAction(),
                            rule != null && rule.isEnabled(),
                            sceneRecords.size(),
                            sceneRecords.stream()
                                    .filter(record -> BLOCKED_OPERATION.equals(record.getOperation()))
                                    .count(),
                            sceneRecords.stream()
                                    .filter(record -> ALERT_OPERATION.equals(record.getOperation()))
                                    .count(),
                            sceneRecords.stream().map(RiskAnalytics::subjectOf).distinct().count());
                })
                .sorted(Comparator.comparingLong(RiskSceneStat::events)
                        .reversed()
                        .thenComparing(RiskSceneStat::scene))
                .toList();
    }

    // 按规则标签、描述或场景标识选择展示名称。
    private String displayLabel(String scene, RiskRule.Spec rule) {
        var label = ruleRegistry.label(scene);
        if (StringUtils.hasText(label)) {
            return label;
        }
        if (rule != null && StringUtils.hasText(rule.getDescription())) {
            return rule.getDescription();
        }
        return scene;
    }

    // 按事件数量排列受影响主体。
    private static List<RiskSubjectStat> subjectStats(List<AuditRecord> records) {
        var recordsBySubject = records.stream()
                .collect(Collectors.groupingBy(RiskAnalytics::subjectOf));
        return recordsBySubject.entrySet().stream()
                .map(entry -> {
                    var scopes = entry.getValue().stream()
                            .map(RiskAnalytics::sceneOf)
                            .collect(Collectors.toCollection(HashSet::new));
                    return new RiskSubjectStat(
                            entry.getKey(),
                            entry.getValue().size(),
                            entry.getValue().stream()
                                    .filter(record -> BLOCKED_OPERATION.equals(record.getOperation()))
                                    .count(),
                            entry.getValue().stream()
                                    .filter(record -> ALERT_OPERATION.equals(record.getOperation()))
                                    .count(),
                            scopes.size());
                })
                .sorted(Comparator.comparingLong(RiskSubjectStat::events)
                        .reversed()
                        .thenComparing(RiskSubjectStat::subject))
                .limit(TOP_SUBJECT_LIMIT)
                .toList();
    }

    // 按场景主体关系生成关系图边数据。
    private static List<RiskRelation> relations(List<AuditRecord> records) {
        return records.stream()
                .collect(Collectors.groupingBy(
                        record -> new SceneSubject(sceneOf(record), subjectOf(record))))
                .entrySet()
                .stream()
                .map(entry -> new RiskRelation(
                        entry.getKey().scene(),
                        entry.getKey().subject(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .filter(record -> BLOCKED_OPERATION.equals(record.getOperation()))
                                .count()))
                .sorted(Comparator.comparingLong(RiskRelation::events)
                        .reversed()
                        .thenComparing(RiskRelation::scene)
                        .thenComparing(RiskRelation::subject))
                .limit(RELATION_LIMIT)
                .toList();
    }

    // 把审计记录转换为最近风险事件视图。
    private static RiskEventView eventView(AuditRecord record) {
        return new RiskEventView(
                auditId(record),
                sceneOf(record),
                subjectOf(record),
                record.getOperation(),
                record.getResult(),
                record.getRequestPath(),
                countOf(record.getContext()),
                record.getTimestamp());
    }

    // 从审计元数据安全读取事件编号。
    private static String auditId(AuditRecord record) {
        return record.getMetadata() == null ? null : record.getMetadata().getName();
    }

    // 优先从上下文读取场景，缺失时回退到资源名称。
    private static String sceneOf(AuditRecord record) {
        var scene = text(record.getContext(), "scene");
        if (scene == null) {
            scene = StringUtils.hasText(record.getResourceName()) ? record.getResourceName() : null;
        }
        return scene == null ? UNKNOWN : scene;
    }

    // 优先从上下文读取主体，缺失时回退到请求 IP。
    private static String subjectOf(AuditRecord record) {
        var subject = text(record.getContext(), "subject");
        if (subject == null) {
            subject = StringUtils.hasText(record.getIp()) ? record.getIp() : null;
        }
        return subject == null ? UNKNOWN : subject;
    }

    // 读取上下文的非空字符串字段。
    private static String text(Map<String, Object> context, String key) {
        var value = context == null ? null : context.get(key);
        return value == null || !StringUtils.hasText(value.toString()) ? null : value.toString();
    }

    // 读取上下文中的命中次数。
    private static long countOf(Map<String, Object> context) {
        var value = context == null ? null : context.get("count");
        return value instanceof Number number ? Math.max(0L, number.longValue()) : 0L;
    }

    // 按事件时间读取发生时刻。
    private static Instant occurredAt(AuditRecord record) {
        return record.getTimestamp();
    }

    private record SceneSubject(String scene, String subject) {}
}
