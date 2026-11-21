package cn.tolim.pudans.core.dashboard.endpoint;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.api.index.query.Queries.in;

import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.ParameterInfoGetter;
import cn.tolim.pudans.infra.api.SystemInfo;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.core.risk.RiskRuleRegistry;
import cn.tolim.pudans.infra.audit.AuditStore;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.fn.builders.apiresponse.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.dashboard.dto.DashboardOverview;
import cn.tolim.pudans.contract.dashboard.dto.ActiveUser;
import cn.tolim.pudans.contract.dashboard.dto.GlobalInfoSummary;
import cn.tolim.pudans.contract.dashboard.dto.RecentOperation;
import cn.tolim.pudans.contract.dashboard.dto.LoginTrendPoint;
import cn.tolim.pudans.contract.dashboard.dto.RoleUserCount;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardEndpoint implements CustomEndpoint {

    private static final int RECENT_AUDIT_LOG_LIMIT = 5;

    private static final int TREND_DAYS = 7;

    private static final int ACTIVE_USER_LIMIT = 5;

    private static final int ACTIVE_USER_SCAN_LIMIT = 10_000;

    private final ReactiveExtensionClient client;

    private final AuditLog auditLog;

    private final ParameterInfoGetter systemInfoGetter;

    private final Groups groups;

    private final RiskRuleRegistry riskRuleRegistry;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "DashboardV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/dashboard/overview", this::overview, builder -> builder
                        .operationId("GetDashboardOverview")
                        .description("Aggregated overview and security posture statistics for the console dashboard.")
                        .tag(tag)
                        .response(Builder.responseBuilder().implementation(DashboardOverview.class)))
                .build();
    }

    private Mono<ServerResponse> overview(ServerRequest request) {
        return aggregate()
                .flatMap(overview -> ServerResponse.ok().bodyValue(overview))
                .onErrorResume(error -> {
                    log.warn("无法聚合仪表盘概览", error);
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "仪表盘聚合失败。"));
                });
    }

    Mono<DashboardOverview> aggregate() {
        var now = Instant.now();
        var dayStart = PudansTime.startOfDay(now);
        var totalUsers = countNotDeleting(User.class);
        var globalInfo = systemInfoGetter
                .get()
                .map(DashboardEndpoint::toSummary)
                .onErrorResume(error -> {
                    log.warn("无法解析系统信息", error);
                    return Mono.just(GlobalInfoSummary.empty());
                });
        var realRoles = listRealRoles();
        var counts = Mono.zip(
                countNotDeleting(UserGroup.class),
                Mono.just((long) riskRuleRegistry.ruleCount()),
                countNotDeleting(Webhook.class));
        var todayOperations = auditLog.countSince(dayStart).onErrorReturn(0L);
        var recentRecords = auditLog
                .findRecent(RECENT_AUDIT_LOG_LIMIT)
                .collectList()
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of())
                .cache();
        var loginTrend = auditLog
                .countPerDay(dayStart.minus(Duration.ofDays(TREND_DAYS - 1L)), "LOGIN", "SUCCESS")
                .map(days -> toDailyTrend(days, now))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var operationTrend = auditLog
                .countPerDay(dayStart.minus(Duration.ofDays(TREND_DAYS - 1L)), null, null)
                .map(days -> toDailyTrend(days, now))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var riskRuleTrend = client
                .listAll(
                        RiskRule.class,
                        ListOptions.builder().andQuery(notDeleting()).build(),
                        defaultSort())
                .collectList()
                .map(items -> toCreationTrend(
                        items.stream()
                                .map(item -> item.getMetadata() == null
                                        ? null
                                        : item.getMetadata().getCreationTimestamp())
                                .toList(),
                        now))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var webhookTrend = client
                .listAll(
                        Webhook.class,
                        ListOptions.builder().andQuery(notDeleting()).build(),
                        defaultSort())
                .collectList()
                .map(items -> toCreationTrend(
                        items.stream()
                                .map(item -> item.getMetadata() == null
                                        ? null
                                        : item.getMetadata().getCreationTimestamp())
                                .toList(),
                        now))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var trends = Mono.zip(loginTrend, operationTrend, riskRuleTrend, webhookTrend)
                .map(tuple -> new TrendData(
                        tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()))
                .defaultIfEmpty(new TrendData(List.of(), List.of(), List.of(), List.of()));
        var activeUserRecords = auditLog
                .query(new AuditStore.AuditQuery(
                        null,
                        dayStart.minus(Duration.ofDays(TREND_DAYS - 1L)),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        ACTIVE_USER_SCAN_LIMIT,
                        false))
                .collectList()
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of())
                .cache();
        var operatorNames = Mono.zip(recentRecords, activeUserRecords)
                .flatMap(tuple -> displayNamesOf(Stream.concat(tuple.getT1().stream(), tuple.getT2().stream())
                        .map(DashboardEndpoint::nullSafeUidOfRecord)
                        .filter(uid -> !"anonymous".equals(uid))
                        .toList()))
                .defaultIfEmpty(Map.of());
        var traffic = Mono.zip(todayOperations, recentRecords, activeUserRecords, operatorNames)
                .map(tuple -> new Traffic(
                        tuple.getT1(),
                        toRecentOperations(tuple.getT2(), tuple.getT4()),
                        toActiveUsers(tuple.getT3(), tuple.getT4())))
                .defaultIfEmpty(new Traffic(0L, List.of(), List.of()));
        return Mono.zip(globalInfo, totalUsers, realRoles, counts, traffic, trends)
                .flatMap(tuple -> toRoleDistribution(tuple.getT3())
                        .map(roleDistribution -> new DashboardOverview(
                                tuple.getT1(),
                                tuple.getT2(),
                                tuple.getT3().size(),
                                tuple.getT4().getT1(),
                                tuple.getT4().getT2(),
                                tuple.getT4().getT3(),
                                tuple.getT5().todayOperations(),
                                tuple.getT5().recentOperations(),
                                tuple.getT6().loginTrend(),
                                tuple.getT6().operationTrend(),
                                tuple.getT6().riskRuleTrend(),
                                tuple.getT6().webhookTrend(),
                                tuple.getT5().activeUsers(),
                                roleDistribution)));
    }

    // 可展示为可分配的角色：排除底层角色模板和隐藏的内部角色。
    private Mono<List<Role>> listRealRoles() {
        return client
                .listAll(Role.class, ListOptions.builder().andQuery(notDeleting()).build(), defaultSort())
                .filter(DashboardEndpoint::isRealRole)
                .collectList();
    }

    private static boolean isRealRole(Role role) {
        var labels = role.getMetadata() == null ? null : role.getMetadata().getLabels();
        if (labels == null) {
            return true;
        }
        return !"true".equals(labels.get(Role.TEMPLATE_LABEL_NAME))
                && !"true".equals(labels.get(Role.HIDDEN_LABEL_NAME));
    }

    private static List<LoginTrendPoint> toDailyTrend(Map<LocalDate, Long> daysByCount, Instant now) {
        var today = PudansTime.dateOf(now);
        var points = new ArrayList<LoginTrendPoint>(TREND_DAYS);
        for (var offset = TREND_DAYS - 1; offset >= 0; offset--) {
            var day = today.minusDays(offset);
            points.add(new LoginTrendPoint(day.toString(), daysByCount.getOrDefault(day, 0L)));
        }
        return points;
    }

    private static List<LoginTrendPoint> toCreationTrend(List<Instant> creationTimestamps, Instant now) {
        var counts = creationTimestamps.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(
                        PudansTime::dateOf,
                        Collectors.counting()));
        return toDailyTrend(counts, now);
    }

    private record TrendData(
            List<LoginTrendPoint> loginTrend,
            List<LoginTrendPoint> operationTrend,
            List<LoginTrendPoint> riskRuleTrend,
            List<LoginTrendPoint> webhookTrend) {}

    private record Traffic(
            long todayOperations, List<RecentOperation> recentOperations, List<ActiveUser> activeUsers) {}

    private static List<ActiveUser> toActiveUsers(List<AuditRecord> records, Map<String, String> names) {
        return records.stream()
                .map(AuditRecord::getUid)
                .filter(java.util.Objects::nonNull)
                .map(uid -> uid.isBlank() ? "anonymous" : uid)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue()
                        .reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(ACTIVE_USER_LIMIT)
                .map(entry -> new ActiveUser(
                        names.getOrDefault(entry.getKey(), entry.getKey()), entry.getValue()))
                .toList();
    }

    private Mono<List<RoleUserCount>> toRoleDistribution(List<Role> realRoles) {
        if (realRoles.isEmpty()) {
            return Mono.just(List.of());
        }
        var realRolesByName = realRoles.stream()
                .collect(Collectors.toMap(role -> role.getMetadata().getName(), Function.identity()));
        return groups
                .listMemberships()
                .collect(Collectors.toMap(
                        Groups.Membership::username,
                        Groups.Membership::groupName,
                        (left, right) -> left))
                .flatMap(groupsByUser -> rolesByGroup(
                                groupsByUser.values().stream().distinct().toList())
                        .map(roles -> toRoleCounts(groupsByUser, roles, realRolesByName)))
                .onErrorReturn(List.of());
    }

    private Mono<Map<String, List<String>>> rolesByGroup(List<String> groupNames) {
        if (groupNames.isEmpty()) {
            return Mono.just(Map.of());
        }
        return groups
                .listUserGroups(groupNames)
                .flatMap(group -> groups
                        .listRoleNamesOfGroup(group)
                        .collectList()
                        .map(roles -> Map.entry(group.getMetadata().getName(), roles)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static List<RoleUserCount> toRoleCounts(
            Map<String, String> groupsByUser,
            Map<String, List<String>> rolesByGroup,
            Map<String, Role> realRolesByName) {
        Map<String, Long> counts = groupsByUser.values().stream()
                .flatMap(group -> rolesByGroup.getOrDefault(group, List.of()).stream())
                .filter(realRolesByName::containsKey)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return counts.entrySet().stream()
                .map(entry -> {
                    var role = realRolesByName.get(entry.getKey());
                    return new RoleUserCount(entry.getKey(), roleDisplayName(role), entry.getValue());
                })
                .sorted((left, right) -> Long.compare(right.users(), left.users()))
                .toList();
    }

    private static String roleDisplayName(Role role) {
        var annotations = role.getMetadata() == null ? null : role.getMetadata().getAnnotations();
        var displayName = annotations == null ? null : annotations.get(Role.DISPLAY_NAME_ANNO);
        if (displayName == null || displayName.isBlank()) {
            return role.getMetadata().getName();
        }
        return displayName;
    }

    private Mono<Map<String, String>> displayNamesOf(Collection<String> uids) {
        if (uids.isEmpty()) {
            return Mono.just(Map.of());
        }
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(in("spec.uid", new HashSet<>(uids)))
                .build();
        return client.listAll(User.class, options, defaultSort())
                .collect(Collectors.toMap(
                        DashboardEndpoint::nullSafeUid,
                        DashboardEndpoint::displayNameOf,
                        (left, right) -> left))
                .defaultIfEmpty(Map.of())
                .onErrorReturn(Map.of());
    }

    private Mono<Long> countNotDeleting(Class<? extends cn.tolim.pudans.extension.api.Extension> type) {
        return client
                .countBy(type, ListOptions.builder().andQuery(notDeleting()).build())
                .onErrorReturn(0L);
    }

    private static List<RecentOperation> toRecentOperations(
            List<AuditRecord> records, Map<String, String> usersByUid) {
        return records.stream()
                .map(record -> toRecentOperation(record, usersByUid))
                .toList();
    }

    private static RecentOperation toRecentOperation(AuditRecord record, Map<String, String> usersByUid) {
        var uid = nullSafeUidOfRecord(record);
        var operator = usersByUid.getOrDefault(uid, uid);
        return new RecentOperation(
                record.getMetadata() == null ? null : record.getMetadata().getName(),
                operator,
                record.getOperation(),
                record.getResourceType(),
                record.getResourceName(),
                record.getResult(),
                record.getTimestamp());
    }

    private static String nullSafeUidOfRecord(AuditRecord record) {
        var uid = record.getUid();
        return uid == null || uid.isBlank() ? "anonymous" : uid;
    }

    private static String nullSafeUid(User user) {
        var spec = user.getSpec();
        var uid = spec == null ? null : spec.getUid();
        return uid == null || uid.isBlank() ? "unknown" : uid;
    }

    private static String displayNameOf(User user) {
        var spec = user.getSpec();
        var displayName = spec == null ? null : spec.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            var name = user.getMetadata() == null ? null : user.getMetadata().getName();
            return name == null ? "unknown" : name;
        }
        return displayName;
    }

    private static GlobalInfoSummary toSummary(SystemInfo info) {
        return new GlobalInfoSummary(
                info.getTitle(),
                info.getSubtitle(),
                info.getUrl() == null ? null : info.getUrl().toString(),
                info.getTimeZone() == null ? null : info.getTimeZone().getID(),
                info.getVersion() == null ? null : info.getVersion().toString(),
                uptimeSeconds());
    }

    private static long uptimeSeconds() {
        try {
            var uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            return Duration.ofMillis(uptimeMillis).toSeconds();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
