package cn.tolim.pudans.core.dashboard.endpoint;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.ExtensionUtil.notDeleting;

import cn.tolim.pudans.contract.core.extension.RiskRule;
import cn.tolim.pudans.contract.core.extension.Webhook;
import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.core.extension.rbac.Role;
import cn.tolim.pudans.contract.core.extension.rbac.UserGroup;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ParameterInfoGetter;
import cn.tolim.pudans.contract.infra.SystemInfo;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.rbac.service.UserGroupService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import reactor.core.publisher.Flux;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class DashboardEndpoint implements CustomEndpoint {

    private static final int RECENT_AUDIT_LOG_LIMIT = 5;

    private static final int LOGIN_TREND_DAYS = 7;

    private final ReactiveExtensionClient client;

    private final AuditService auditService;

    private final ParameterInfoGetter systemInfoGetter;

    private final UserGroupService userGroupService;

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
                    log.warn("Unable to aggregate the dashboard overview", error);
                    return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR, "Dashboard aggregation failed."));
                });
    }

    Mono<DashboardOverview> aggregate() {
        var now = Instant.now();
        var dayStart = now.truncatedTo(ChronoUnit.DAYS);
        var users = client
                .listAll(User.class, ListOptions.builder().andQuery(notDeleting()).build(), defaultSort())
                .collectList();
        var globalInfo = systemInfoGetter
                .get()
                .map(DashboardEndpoint::toSummary)
                .onErrorResume(error -> {
                    log.warn("Unable to resolve the system info", error);
                    return Mono.just(GlobalInfoSummary.empty());
                });
        var realRoles = listRealRoles();
        var counts = Mono.zip(
                countNotDeleting(UserGroup.class),
                countNotDeleting(RiskRule.class),
                countNotDeleting(Webhook.class));
        var todayOperations = auditService.countSince(dayStart).onErrorReturn(0L);
        var recentOperations = auditService
                .findRecent(RECENT_AUDIT_LOG_LIMIT)
                .collectList()
                .flatMap(this::toRecentOperations)
                .onErrorReturn(List.of());
        var loginTrend = auditService
                .countPerDay(dayStart.minus(Duration.ofDays(LOGIN_TREND_DAYS - 1L)), "LOGIN", "SUCCESS")
                .map(days -> toLoginTrend(days, now))
                .defaultIfEmpty(List.of())
                .onErrorReturn(List.of());
        return Mono.zip(globalInfo, users, realRoles, counts, todayOperations, recentOperations, loginTrend)
                .flatMap(tuple -> toRoleDistribution(tuple.getT2(), tuple.getT3())
                        .map(roleDistribution -> new DashboardOverview(
                                tuple.getT1(),
                                tuple.getT2().size(),
                                tuple.getT3().size(),
                                tuple.getT4().getT1(),
                                tuple.getT4().getT2(),
                                tuple.getT4().getT3(),
                                tuple.getT5(),
                                tuple.getT6(),
                                tuple.getT7(),
                                roleDistribution)));
    }

    /**
     * Roles that may be shown as assignable: everything except the underlying
     * role templates and hidden internal roles.
     */
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

    private static List<LoginTrendPoint> toLoginTrend(Map<LocalDate, Long> daysByCount, Instant now) {
        var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var points = new ArrayList<LoginTrendPoint>(LOGIN_TREND_DAYS);
        for (var offset = LOGIN_TREND_DAYS - 1; offset >= 0; offset--) {
            var day = today.minusDays(offset);
            points.add(new LoginTrendPoint(day.toString(), daysByCount.getOrDefault(day, 0L)));
        }
        return points;
    }

    private Mono<List<RoleUserCount>> toRoleDistribution(List<User> users, List<Role> realRoles) {
        if (users.isEmpty() || realRoles.isEmpty()) {
            return Mono.just(List.of());
        }
        var realRolesByName = realRoles.stream()
                .collect(Collectors.toMap(role -> role.getMetadata().getName(), Function.identity()));
        return Flux.fromIterable(users)
                .flatMap(
                        user -> userGroupService
                                .getRoleNamesByUsername(user.getMetadata().getName())
                                .collectList()
                                .map(roles -> Map.entry(user.getMetadata().getName(), roles)),
                        4)
                .collectMap(Map.Entry::getKey, entry -> entry.getValue())
                .defaultIfEmpty(new LinkedHashMap<>())
                .map(assignments -> {
                    Map<String, Long> counts = assignments.values().stream()
                            .flatMap(Collection::stream)
                            .filter(realRolesByName::containsKey)
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                    return counts.entrySet().stream()
                            .map(entry -> {
                                var role = realRolesByName.get(entry.getKey());
                                return new RoleUserCount(entry.getKey(), roleDisplayName(role), entry.getValue());
                            })
                            .sorted((left, right) -> Long.compare(right.users(), left.users()))
                            .toList();
                })
                .onErrorReturn(List.of());
    }

    private static String roleDisplayName(Role role) {
        var annotations = role.getMetadata() == null ? null : role.getMetadata().getAnnotations();
        var displayName = annotations == null ? null : annotations.get(DISPLAY_NAME_ANNO);
        if (displayName == null || displayName.isBlank()) {
            return role.getMetadata().getName();
        }
        return displayName;
    }

    private Mono<Long> countNotDeleting(Class<? extends cn.tolim.pudans.contract.extension.Extension> type) {
        return client
                .countBy(type, ListOptions.builder().andQuery(notDeleting()).build())
                .onErrorReturn(0L);
    }

    private static final String DISPLAY_NAME_ANNO = "rbac.authorization.pudans.tolim.cn/display-name";

    private Mono<List<RecentOperation>> toRecentOperations(List<AuditRecord> records) {
        if (records.isEmpty()) {
            return Mono.just(List.of());
        }
        return client
                .listAll(User.class, ListOptions.builder().andQuery(notDeleting()).build(), defaultSort())
                .collectMap(
                        user -> nullSafeUid(user),
                        user -> displayNameOf(user),
                        () -> new LinkedHashMap<String, String>())
                .defaultIfEmpty(new LinkedHashMap<>())
                .map(users -> records.stream()
                        .map(record -> toRecentOperation(record, users))
                        .toList());
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
                info.getLocale() == null ? null : info.getLocale().toString(),
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

    @Schema(description = "Aggregated dashboard overview with site info and security posture counters.")
    public record DashboardOverview(
            GlobalInfoSummary globalInfo,
            long totalUsers,
            long totalRoles,
            long totalUserGroups,
            long totalRiskRules,
            long totalWebhooks,
            long todayOperations,
            List<RecentOperation> recentAuditLogs,
            List<LoginTrendPoint> loginTrend,
            List<RoleUserCount> roleDistribution) {}

    @Schema(description = "Basic site information aggregated from the system info service.")
    public record GlobalInfoSummary(
            String siteTitle,
            String siteSubtitle,
            String externalUrl,
            String timezone,
            String locale,
            String version,
            long uptimeSeconds) {

        public static GlobalInfoSummary empty() {
            return new GlobalInfoSummary(null, null, null, null, null, null, 0L);
        }
    }

    @Schema(description = "One recent audit operation rendered on the dashboard.")
    public record RecentOperation(
            String auditId,
            String operator,
            String operation,
            String resourceType,
            String resourceName,
            String result,
            Instant timestamp) {}

    @Schema(description = "Successful login count of one UTC day.")
    public record LoginTrendPoint(String date, long count) {}

    @Schema(description = "Number of accounts holding one role.")
    public record RoleUserCount(String role, String displayName, long users) {}
}
