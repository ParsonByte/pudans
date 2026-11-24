package cn.tolim.pudans.infra.dashboard.endpoint;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.contract.index.query.Queries.greaterThan;
import static cn.tolim.pudans.extension.contract.index.query.Queries.in;
import static cn.tolim.pudans.extension.contract.index.query.Queries.lessThan;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;

import cn.tolim.pudans.contract.risk.RiskRule;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.contract.rbac.UserGroup;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.ParameterInfoGetter;
import cn.tolim.pudans.infra.contract.SystemInfo;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.core.risk.RiskRuleRegistry;
import cn.tolim.pudans.infra.audit.AuditStore;
import cn.tolim.pudans.infra.utils.RangeDays;
import cn.tolim.pudans.infra.utils.PudansTime;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import cn.tolim.pudans.infra.contract.dashboard.DashboardOverview;
import cn.tolim.pudans.infra.contract.dashboard.DashboardInsight;
import cn.tolim.pudans.infra.contract.dashboard.DashboardMetric;
import cn.tolim.pudans.infra.contract.dashboard.ActiveUser;
import cn.tolim.pudans.infra.contract.dashboard.GlobalInfoSummary;
import cn.tolim.pudans.infra.contract.dashboard.OperationCount;
import cn.tolim.pudans.infra.contract.dashboard.RecentOperation;
import cn.tolim.pudans.infra.contract.dashboard.ResultCount;
import cn.tolim.pudans.infra.contract.dashboard.LoginTrendPoint;
import cn.tolim.pudans.infra.contract.dashboard.RoleUserCount;

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardEndpoint implements CustomEndpoint {

    private static final int RECENT_AUDIT_LOG_LIMIT = 5;

    private static final int ACTIVE_USER_LIMIT = 5;

    private static final int ACTIVE_USER_SCAN_LIMIT = 10_000;

    private static final int TOP_OPERATION_LIMIT = 6;

    private static final int INSIGHT_LIMIT = 4;

    private final ReactiveExtensionClient client;

    private final AuditLog auditLog;

    private final ParameterInfoGetter systemInfoGetter;

    private final Groups groups;

    private final RiskRuleRegistry riskRuleRegistry;

    private final RiskGuard riskGuard;

    // 注册仪表盘概览路由并声明响应模型。
    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "DashboardV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/dashboard/overview", this::overview, builder -> builder
                        .operationId("GetDashboardOverview")
                        .description("Aggregated overview and security posture statistics for the console dashboard.")
                        .tag(tag)
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("days")
                                .required(false)
                                .description("Trend and comparison range in days, between 1 and 90. Defaults to 7.")
                                .implementation(Integer.class))
                        .parameter(parameterBuilder()
                                .in(ParameterIn.QUERY)
                                .name("to")
                                .required(false)
                                .description("Inclusive range end as an ISO-8601 instant. Defaults to now.")
                                .implementation(String.class))
                        .response(Builder.responseBuilder().implementation(DashboardOverview.class)))
                .build();
    }

    // 聚合失败时记录日志并返回统一的服务端错误。
    private Mono<ServerResponse> overview(ServerRequest request) {
        return aggregate(RangeDays.resolve(request), RangeDays.resolveEnd(request))
                .flatMap(overview -> ServerResponse.ok().bodyValue(overview))
                .onErrorResume(error -> {
                    log.warn("无法聚合仪表盘概览", error);
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "仪表盘聚合失败。"));
                });
    }

    // 使用默认时间范围聚合并保持旧调用方式。
    Mono<DashboardOverview> aggregate() {
        return aggregate(RangeDays.DEFAULT);
    }

    // 使用默认结束时刻聚合并保持旧调用方式。
    Mono<DashboardOverview> aggregate(int days) {
        return aggregate(days, Instant.now());
    }

    // 并行汇总用户、角色、审计与趋势数据。
    Mono<DashboardOverview> aggregate(int days, Instant rangeEnd) {
        var generatedAt = Instant.now();
        var dayStart = PudansTime.startOfDay(rangeEnd);
        var rangeFrom = dayStart.minus(Duration.ofDays(days - 1L));
        var previousFrom = rangeFrom.minus(Duration.ofDays(days));
        var totalUsers = countNotDeleting(User.class);
        var newUsersCurrent = countCreatedBetween(User.class, rangeFrom, rangeEnd);
        var newUsersPrevious = countCreatedBetween(User.class, previousFrom, rangeFrom);
        var globalInfo = systemInfoGetter
                .get()
                .map(DashboardEndpoint::toSummary)
                .onErrorResume(error -> {
                    log.warn("无法解析系统信息", error);
                    return Mono.just(GlobalInfoSummary.empty());
                });
        var realRoles = listRealRoles().cache();
        var counts = Mono.zip(
                countNotDeleting(UserGroup.class),
                Mono.just((long) riskRuleRegistry.ruleCount()),
                countNotDeleting(Webhook.class));
        var todayOperations = auditLog
                .countSince(PudansTime.startOfDay(generatedAt))
                .onErrorReturn(0L);
        var recentRecords = auditLog
                .findRecent(rangeFrom, rangeEnd, RECENT_AUDIT_LOG_LIMIT)
                .collectList()
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of())
                .cache();
        var loginTrend = auditLog
                .countPerDay(rangeFrom, rangeEnd, null, "LOGIN", "SUCCESS")
                .map(daysByCount -> toDailyTrend(daysByCount, rangeEnd, days))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var operationTrend = auditLog
                .countPerDay(rangeFrom, rangeEnd, null, null, null)
                .map(daysByCount -> toDailyTrend(daysByCount, rangeEnd, days))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var failureTrend = Mono.zip(
                        auditLog.countPerDay(rangeFrom, rangeEnd, null, null, "FAILURE"),
                        auditLog.countPerDay(rangeFrom, rangeEnd, null, null, "DENIED"))
                .map(tuple -> toDailyTrend(mergeCounts(tuple.getT1(), tuple.getT2()), rangeEnd, days))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var riskRules = listNotDeleting(RiskRule.class).cache();
        var riskRuleTrend = riskRules
                .map(items -> toCreationTrend(creationTimestampsOf(items), rangeEnd, days))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var newRiskRulesCurrent =
                riskRules.map(items -> countCreatedBetween(items, rangeFrom, rangeEnd)).onErrorReturn(0L);
        var newRiskRulesPrevious =
                riskRules.map(items -> countCreatedBetween(items, previousFrom, rangeFrom)).onErrorReturn(0L);
        var webhooks = listNotDeleting(Webhook.class).cache();
        var webhookTrend = webhooks
                .map(items -> toCreationTrend(creationTimestampsOf(items), rangeEnd, days))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        var trends = Mono.zip(loginTrend, operationTrend, failureTrend, riskRuleTrend, webhookTrend)
                .map(tuple -> new TrendData(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5()))
                .defaultIfEmpty(new TrendData(
                        List.of(), List.of(), List.of(), List.of(), List.of()));
        var activeUserRecords = auditLog
                .query(new AuditStore.AuditQuery(
                        null,
                        rangeFrom,
                        rangeEnd,
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
        var resultCounts = auditLog.countByResult(rangeFrom, rangeEnd, null).onErrorReturn(Map.of());
        var previousResultCounts =
                auditLog.countByResult(previousFrom, rangeFrom, null).onErrorReturn(Map.of());
        var loginResultCounts =
                auditLog.countByResult(rangeFrom, rangeEnd, "LOGIN").onErrorReturn(Map.of());
        var previousLoginResultCounts =
                auditLog.countByResult(previousFrom, rangeFrom, "LOGIN").onErrorReturn(Map.of());
        var activeUserCount = auditLog.countDistinctUids(rangeFrom, rangeEnd).onErrorReturn(0L);
        var previousActiveUserCount =
                auditLog.countDistinctUids(previousFrom, rangeFrom).onErrorReturn(0L);
        var topOperations = auditLog
                .topOperations(rangeFrom, rangeEnd, TOP_OPERATION_LIMIT)
                .onErrorReturn(Map.of());
        var activeBans = riskGuard.listBans().count().onErrorReturn(0L);
        var auditStats = Mono.zip(
                        resultCounts,
                        previousResultCounts,
                        loginResultCounts,
                        previousLoginResultCounts,
                        activeUserCount,
                        previousActiveUserCount,
                        topOperations,
                        activeBans)
                .map(tuple -> toAuditStats(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5(),
                        tuple.getT6(),
                        tuple.getT7(),
                        tuple.getT8()));
        var traffic = Mono.zip(todayOperations, recentRecords, activeUserRecords, operatorNames)
                .map(tuple -> new Traffic(
                        tuple.getT1(),
                        toRecentOperations(tuple.getT2(), tuple.getT4()),
                        toActiveUsers(tuple.getT3(), tuple.getT4())))
                .defaultIfEmpty(new Traffic(0L, List.of(), List.of()));
        var memberships = groups
                .listMemberships()
                .collectList()
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of())
                .cache();
        var growth = Mono.zip(newUsersCurrent, newUsersPrevious, newRiskRulesCurrent, newRiskRulesPrevious)
                .map(tuple -> new Growth(
                        tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()));
        var identityData = Mono.zip(growth, memberships, realRoles)
                .flatMap(tuple -> buildRoleDistribution(tuple.getT3(), tuple.getT2())
                        .map(roleDistribution -> new IdentityData(
                                tuple.getT1(),
                                roleDistribution,
                                distinctMembershipUsers(tuple.getT2()))));
        return Mono.zip(
                        globalInfo,
                        totalUsers,
                        realRoles,
                        counts,
                        traffic,
                        trends,
                        identityData,
                        auditStats)
                .map(tuple -> new DashboardOverview(
                        tuple.getT1(),
                        days,
                        generatedAt,
                        tuple.getT2(),
                        tuple.getT3().size(),
                        tuple.getT4().getT1(),
                        tuple.getT4().getT2(),
                        tuple.getT4().getT3(),
                        tuple.getT5().todayOperations(),
                        tuple.getT5().recentOperations(),
                        tuple.getT6().loginTrend(),
                        tuple.getT6().operationTrend(),
                        tuple.getT6().failureTrend(),
                        tuple.getT6().riskRuleTrend(),
                        tuple.getT6().webhookTrend(),
                        tuple.getT5().activeUsers(),
                        tuple.getT7().roleDistribution(),
                        tuple.getT8().resultDistribution(),
                        tuple.getT8().topOperations(),
                        buildMetrics(tuple.getT2(), tuple.getT4().getT2(), tuple.getT7(), tuple.getT8()),
                        buildInsights(tuple.getT2(), tuple.getT7(), tuple.getT8(), days)));
    }

    // 可展示为可分配的角色：排除底层角色模板和隐藏的内部角色。
    private Mono<List<Role>> listRealRoles() {
        return client
                .listAll(Role.class, ListOptions.builder().andQuery(notDeleting()).build(), defaultSort())
                .filter(DashboardEndpoint::isRealRole)
                .collectList();
    }

    // 排除角色模板与隐藏角色，只保留可分配角色。
    private static boolean isRealRole(Role role) {
        var labels = role.getMetadata() == null ? null : role.getMetadata().getLabels();
        if (labels == null) {
            return true;
        }
        return !"true".equals(labels.get(Role.TEMPLATE_LABEL_NAME))
                && !"true".equals(labels.get(Role.HIDDEN_LABEL_NAME));
    }

    // 将按日计数补全为连续的趋势点序列。
    private static List<LoginTrendPoint> toDailyTrend(
            Map<LocalDate, Long> daysByCount, Instant now, int rangeDays) {
        var today = PudansTime.dateOf(now);
        var points = new ArrayList<LoginTrendPoint>(rangeDays);
        for (var offset = rangeDays - 1; offset >= 0; offset--) {
            var day = today.minusDays(offset);
            points.add(new LoginTrendPoint(day.toString(), daysByCount.getOrDefault(day, 0L)));
        }
        return points;
    }

    // 按创建时间聚合对象数量，生成新增趋势。
    private static List<LoginTrendPoint> toCreationTrend(
            List<Instant> creationTimestamps, Instant now, int rangeDays) {
        var counts = creationTimestamps.stream()
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.groupingBy(
                        PudansTime::dateOf,
                        Collectors.counting()));
        return toDailyTrend(counts, now, rangeDays);
    }

    private record TrendData(
            List<LoginTrendPoint> loginTrend,
            List<LoginTrendPoint> operationTrend,
            List<LoginTrendPoint> failureTrend,
            List<LoginTrendPoint> riskRuleTrend,
            List<LoginTrendPoint> webhookTrend) {}

    private record Traffic(
            long todayOperations, List<RecentOperation> recentOperations, List<ActiveUser> activeUsers) {}

    private record Growth(
            long newUsersCurrent,
            long newUsersPrevious,
            long newRiskRulesCurrent,
            long newRiskRulesPrevious) {}

    private record IdentityData(
            Growth growth,
            List<RoleUserCount> roleDistribution,
            long membershipUsers) {}

    private record AuditStats(
            long operations,
            long previousOperations,
            long activeUsers,
            long previousActiveUsers,
            long loginSuccess,
            long previousLoginSuccess,
            long loginFailure,
            long previousLoginFailure,
            long abnormalOperations,
            long previousAbnormalOperations,
            long activeBans,
            List<ResultCount> resultDistribution,
            List<OperationCount> topOperations) {}

    // 按操作次数排序并截取最活跃的匿名或已知用户。
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

    // 依据用户组关系计算角色分布。
    private Mono<List<RoleUserCount>> buildRoleDistribution(
            List<Role> realRoles, List<Groups.Membership> memberships) {
        if (realRoles.isEmpty() || memberships.isEmpty()) {
            return Mono.just(List.of());
        }
        var realRolesByName = realRoles.stream()
                .collect(Collectors.toMap(role -> role.getMetadata().getName(), Function.identity()));
        var groupsByUser = memberships.stream()
                .collect(Collectors.toMap(
                        Groups.Membership::username,
                        Groups.Membership::groupName,
                        (left, right) -> left));
        return rolesByGroup(groupsByUser.values().stream().distinct().toList())
                .map(roles -> toRoleCounts(groupsByUser, roles, realRolesByName))
                .onErrorReturn(List.of());
    }

    // 批量查询用户组绑定的角色名称。
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

    // 汇总每个角色的用户数并按数量降序排列。
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

    // 优先返回角色显示名，缺省时回退到资源名。
    private static String roleDisplayName(Role role) {
        var annotations = role.getMetadata() == null ? null : role.getMetadata().getAnnotations();
        var displayName = annotations == null ? null : annotations.get(Role.DISPLAY_NAME_ANNO);
        if (displayName == null || displayName.isBlank()) {
            return role.getMetadata().getName();
        }
        return displayName;
    }

    // 批量解析用户显示名，失败时退化为空映射。
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

    // 统计未删除的指定类型资源数量，失败按零处理。
    private Mono<Long> countNotDeleting(Class<? extends cn.tolim.pudans.extension.contract.Extension> type) {
        return client
                .countBy(type, ListOptions.builder().andQuery(notDeleting()).build())
                .onErrorReturn(0L);
    }

    // 读取未删除的扩展资源列表，失败时返回空列表。
    private <E extends Extension> Mono<List<E>> listNotDeleting(Class<E> type) {
        return client
                .listAll(type, ListOptions.builder().andQuery(notDeleting()).build(), defaultSort())
                .collectList()
                .defaultIfEmpty(List.of());
    }

    // 统计指定创建时间区间内的未删除资源数量。
    private Mono<Long> countCreatedBetween(
            Class<? extends Extension> type, Instant from, Instant to) {
        var options = ListOptions.builder()
                .andQuery(notDeleting())
                .andQuery(greaterThan("metadata.creationTimestamp", from, true))
                .andQuery(lessThan("metadata.creationTimestamp", to, false))
                .build();
        return client.countBy(type, options).onErrorReturn(0L);
    }

    // 提取扩展资源的创建时间。
    private static List<Instant> creationTimestampsOf(List<? extends Extension> items) {
        return items.stream()
                .map(item -> item.getMetadata() == null
                        ? null
                        : item.getMetadata().getCreationTimestamp())
                .toList();
    }

    // 统计列表中指定创建时间区间的对象数量。
    private static long countCreatedBetween(
            List<? extends Extension> items, Instant from, Instant to) {
        return items.stream()
                .filter(item -> item.getMetadata() != null)
                .map(item -> item.getMetadata().getCreationTimestamp())
                .filter(java.util.Objects::nonNull)
                .filter(created -> !created.isBefore(from) && created.isBefore(to))
                .count();
    }

    // 合并两组按日计数。
    private static Map<LocalDate, Long> mergeCounts(
            Map<LocalDate, Long> left, Map<LocalDate, Long> right) {
        var merged = new LinkedHashMap<>(left);
        right.forEach((day, count) -> merged.merge(day, count, Long::sum));
        return merged;
    }

    // 从结果分布中计算审计指标。
    private static AuditStats toAuditStats(
            Map<String, Long> results,
            Map<String, Long> previousResults,
            Map<String, Long> loginResults,
            Map<String, Long> previousLoginResults,
            long activeUsers,
            long previousActiveUsers,
            Map<String, Long> topOperations,
            long activeBans) {
        var loginSuccess = countOf(loginResults, "SUCCESS");
        var previousLoginSuccess = countOf(previousLoginResults, "SUCCESS");
        var loginFailure = sumCounts(loginResults) - loginSuccess;
        var previousLoginFailure = sumCounts(previousLoginResults) - previousLoginSuccess;
        var abnormalOperations = countOf(results, "FAILURE") + countOf(results, "DENIED");
        var previousAbnormalOperations =
                countOf(previousResults, "FAILURE") + countOf(previousResults, "DENIED");
        return new AuditStats(
                sumCounts(results),
                sumCounts(previousResults),
                activeUsers,
                previousActiveUsers,
                loginSuccess,
                previousLoginSuccess,
                loginFailure,
                previousLoginFailure,
                abnormalOperations,
                previousAbnormalOperations,
                activeBans,
                toResultCounts(results),
                toOperationCounts(topOperations));
    }

    // 汇总全部结果数量。
    private static long sumCounts(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    // 读取指定结果的计数。
    private static long countOf(Map<String, Long> counts, String result) {
        return counts.getOrDefault(result, 0L);
    }

    // 将结果计数映射转换为接口模型。
    private static List<ResultCount> toResultCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> new ResultCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    // 将高频操作映射转换为接口模型。
    private static List<OperationCount> toOperationCounts(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .map(entry -> new OperationCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    // 生成设备驾驶舱顶部指标。
    private static List<DashboardMetric> buildMetrics(
            long totalUsers,
            long totalRiskRules,
            IdentityData identity,
            AuditStats audit) {
        var loginTotal = audit.loginSuccess() + audit.loginFailure();
        var previousLoginTotal = audit.previousLoginSuccess() + audit.previousLoginFailure();
        var loginSuccessRate = loginTotal == 0 ? 0.0 : audit.loginSuccess() * 100.0 / loginTotal;
        var previousLoginSuccessRate = previousLoginTotal == 0
                ? 0.0
                : audit.previousLoginSuccess() * 100.0 / previousLoginTotal;
        return List.of(
                new DashboardMetric(
                        "users",
                        totalUsers,
                        Math.max(0, totalUsers - identity.growth().newUsersCurrent())),
                new DashboardMetric("activeUsers", audit.activeUsers(), audit.previousActiveUsers()),
                new DashboardMetric("operations", audit.operations(), audit.previousOperations()),
                new DashboardMetric("loginSuccess", audit.loginSuccess(), audit.previousLoginSuccess()),
                new DashboardMetric("loginSuccessRate", loginSuccessRate, previousLoginSuccessRate),
                new DashboardMetric(
                        "newUsers",
                        identity.growth().newUsersCurrent(),
                        identity.growth().newUsersPrevious()),
                new DashboardMetric(
                        "abnormalOperations",
                        audit.abnormalOperations(),
                        audit.previousAbnormalOperations()),
                new DashboardMetric(
                        "riskRules",
                        totalRiskRules,
                        Math.max(0, totalRiskRules - identity.growth().newRiskRulesCurrent())));
    }

    // 生成基于真实数据的安全与身份诊断。
    private static List<DashboardInsight> buildInsights(
            long totalUsers, IdentityData identity, AuditStats audit, int days) {
        var insights = new ArrayList<DashboardInsight>();
        if (audit.activeBans() > 0) {
            insights.add(new DashboardInsight(
                    "CRITICAL",
                    "存在生效中的风险封禁",
                    audit.activeBans() + " 个主体处于封禁状态，建议前往风险页面处理。"));
        }
        var loginTotal = audit.loginSuccess() + audit.loginFailure();
        var loginFailureRate = loginTotal == 0 ? 0.0 : audit.loginFailure() * 100.0 / loginTotal;
        if (loginTotal > 0 && loginFailureRate >= 10.0) {
            insights.add(new DashboardInsight(
                    "WARNING",
                    "登录失败率偏高",
                    "近 " + days + " 天登录失败 " + audit.loginFailure() + " 次，占比 "
                            + String.format("%.1f", loginFailureRate) + "%。"));
        }
        var abnormalRate = audit.operations() == 0
                ? 0.0
                : audit.abnormalOperations() * 100.0 / audit.operations();
        if (audit.operations() > 0 && abnormalRate >= 5.0) {
            insights.add(new DashboardInsight(
                    "WARNING",
                    "检测到异常操作",
                    "失败或拒绝操作 " + audit.abnormalOperations() + " 次，占比 "
                            + String.format("%.1f", abnormalRate) + "%。"));
        }
        var orphanUsers = Math.max(0, totalUsers - identity.membershipUsers());
        if (orphanUsers > 0 && totalUsers > 0) {
            insights.add(new DashboardInsight(
                    "INFO",
                    "存在未加入用户组的用户",
                    orphanUsers + " 个用户尚未加入任何用户组，权限继承可能不完整。"));
        }
        if (insights.isEmpty()) {
            insights.add(new DashboardInsight(
                    "SUCCESS", "身份与安全基线正常", "当前时间范围内未发现需要立即处理的风险项。"));
        }
        return insights.stream().limit(INSIGHT_LIMIT).toList();
    }

    // 统计有用户组成员的去重用户数。
    private static long distinctMembershipUsers(List<Groups.Membership> memberships) {
        return memberships.stream().map(Groups.Membership::username).distinct().count();
    }

    // 将审计记录转换为最近操作视图。
    private static List<RecentOperation> toRecentOperations(
            List<AuditRecord> records, Map<String, String> usersByUid) {
        return records.stream()
                .map(record -> toRecentOperation(record, usersByUid))
                .toList();
    }

    // 解析操作人显示名并组装单条操作视图。
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

    // 将缺失或空白的用户标识统一为匿名。
    private static String nullSafeUidOfRecord(AuditRecord record) {
        var uid = record.getUid();
        return uid == null || uid.isBlank() ? "anonymous" : uid;
    }

    // 读取用户标识，缺失时返回未知占位值。
    private static String nullSafeUid(User user) {
        var spec = user.getSpec();
        var uid = spec == null ? null : spec.getUid();
        return uid == null || uid.isBlank() ? "unknown" : uid;
    }

    // 优先使用显示名，缺失时回退到用户名。
    private static String displayNameOf(User user) {
        var spec = user.getSpec();
        var displayName = spec == null ? null : spec.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            var name = user.getMetadata() == null ? null : user.getMetadata().getName();
            return name == null ? "unknown" : name;
        }
        return displayName;
    }

    // 将系统信息裁剪为仪表盘需要的公开摘要。
    private static GlobalInfoSummary toSummary(SystemInfo info) {
        return new GlobalInfoSummary(
                info.getTitle(),
                info.getSubtitle(),
                info.getUrl() == null ? null : info.getUrl().toString(),
                info.getTimeZone() == null ? null : info.getTimeZone().getID(),
                info.getVersion() == null ? null : info.getVersion().toString(),
                uptimeSeconds());
    }

    // 读取进程运行时长，不可用时返回零。
    private static long uptimeSeconds() {
        try {
            var uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            return Duration.ofMillis(uptimeMillis).toSeconds();
        } catch (RuntimeException e) {
            return 0L;
        }
    }

    // 声明仪表盘接口所属的版本。
    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }
}
