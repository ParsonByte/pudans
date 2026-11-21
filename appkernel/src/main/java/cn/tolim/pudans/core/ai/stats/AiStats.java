package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver.Viewer;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.model.AiModelRequestRepository.ModelStatsRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.AccountActivityRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.DailyRoutingRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.KnowledgeBaseStatsRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.MemoryStatsRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.PointStatsRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.RecentChatRequestRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.RoutingHistoryRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.RoutingStatsRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.RoutingSummaryRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.UserActivityRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiUsageRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiUsageRepository.RequestDetailRow;
import cn.tolim.pudans.core.infra.utils.PudansTime;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiStats {

    private final AiStatsRepository repository;

    private final AiModelRequestRepository modelRequestRepository;

    private final Accounts userService;

    private final AiUsageRepository usageRepository;

    private Clock clock = PudansTime.systemClock();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<RoutingStats> routing(int days) {
        return repository.routing(days(days)).collectList().map(RoutingStats::new);
    }

    public Mono<PointStats> units(int days) {
        return repository.units(days(days)).collectList().map(PointStats::new);
    }

    public Mono<RoutingLive> routingLive(int days, int limit) {
        var window = days(days);
        var size = limit(limit);
        var users = repository
                .userActivity(window, size)
                .collectList()
                .flatMap(rows -> Flux.fromIterable(rows)
                        .concatMap(row -> userService
                                .getUserByUid(row.userId())
                                .map(user -> RoutingLive.user(row, user))
                                .defaultIfEmpty(RoutingLive.user(row, null)))
                        .collectList());
        return Mono.zip(
                        repository.routingSummary(window),
                        repository.routingDays(window).collectList(),
                        repository.recentChatRequests(window, size).collectList(),
                        repository.accountActivityToday().collectList(),
                        users)
                .map(tuple -> new RoutingLive(
                        RoutingLive.summary(tuple.getT1()),
                        tuple.getT2().stream().map(RoutingLive::daily).toList(),
                        tuple.getT3().stream().map(RoutingLive::request).toList(),
                        tuple.getT4().stream().map(RoutingLive::account).toList(),
                        tuple.getT5()));
    }

    public Mono<MemoryStatsRow> memory() {
        return repository.memory();
    }

    public Mono<KnowledgeBaseStatsRow> knowledgeBase() {
        return repository.knowledgeBase();
    }

    public Mono<ModelStats> models(int days) {
        return modelRequestRepository.stats(days(days)).collectList().map(ModelStats::new);
    }

    public Mono<RoutingHistoryPage> routingHistory(
            RoutingHistoryQuery query, long beforeId, int limit, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        requireHistoryPage(beforeId, limit);
        return repository
                .routingHistory(
                        window.fromAt(),
                        window.toAt(),
                        text(query.status()),
                        text(query.provider()),
                        text(query.model()),
                        text(query.agent()),
                        text(query.group()),
                        viewer.all(),
                        viewer.userId(),
                        beforeId,
                        limit + 1)
                .collectList()
                .map(rows -> new RoutingHistoryPage(
                        rows.stream().limit(limit).map(AiStats::historyItem).toList(),
                        rows.size() > limit ? rows.get(limit - 1).id() : null));
    }

    public Mono<RoutingHistoryDetail> routingHistoryDetail(String requestId, Viewer viewer) {
        return usageRepository
                .requestDetail(requestId)
                .filter(row -> viewer.all() || row.userId().equals(viewer.userId()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, AiUsageErrorCodes.REQUEST_NOT_FOUND, "请求不存在。")))
                .map(AiStats::historyDetail);
    }

    private static void requireHistoryPage(long beforeId, int limit) {
        if (limit < 1 || limit > AiUsage.MAX_LIMIT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.LIMIT_INVALID, "limit 必须在 1 到 200 之间。");
        }
        if (beforeId < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.CURSOR_INVALID, "beforeId 必须大于零。");
        }
    }

    private static String text(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static RoutingHistoryItem historyItem(RoutingHistoryRow row) {
        return new RoutingHistoryItem(
                row.requestId(),
                row.conversationId(),
                row.sessionKey(),
                row.sessionTitle(),
                row.agent(),
                row.mode(),
                row.routeGroup(),
                row.provider(),
                row.account(),
                row.model(),
                row.tier(),
                row.status(),
                row.tries(),
                row.latencyMs(),
                row.createdAt());
    }

    private static RoutingHistoryDetail historyDetail(RequestDetailRow row) {
        return new RoutingHistoryDetail(
                row.requestId(),
                row.conversationId(),
                row.sessionKey(),
                row.agent(),
                row.mode(),
                row.routeGroup(),
                row.provider(),
                row.account(),
                row.model(),
                row.tier(),
                row.status(),
                tries(row.routeTrace()),
                row.latencyMs(),
                row.createdAt(),
                row.completedAt(),
                row.routeTrace(),
                row.retrievalTrace());
    }

    // 历史列表由 route_trace.tries 推导重试次数，缺失时返回 0。
    private static long tries(@Nullable JsonNode trace) {
        if (trace == null) {
            return 0;
        }
        var node = trace.get("tries");
        return node == null || node.isNull() ? 0 : Math.max(node.asLong(0), 0);
    }

    private static int days(int days) {
        if (days < 1 || days > 90) {
            throw new IllegalArgumentException("days 必须在 1 到 90 之间。");
        }
        return days;
    }

    private static int limit(int limit) {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit 必须在 1 到 200 之间。");
        }
        return limit;
    }

    public record RoutingLive(
            Summary summary,
            List<DailyRouting> daily,
            List<RecentRequest> requests,
            List<AccountActivity> accounts,
            List<RoutingUser> users) {

        private static RoutingUser user(UserActivityRow row, @Nullable User user) {
            var spec = user == null ? null : user.getSpec();
            return new RoutingUser(
                    row.userId(),
                    user == null ? null : user.getMetadata().getName(),
                    spec == null ? null : spec.getDisplayName(),
                    row.requests(),
                    row.failed(),
                    row.lastRequestAt());
        }

        private static Summary summary(RoutingSummaryRow row) {
            return new Summary(
                    row.requests(),
                    row.failed(),
                    row.running(),
                    row.inputTokens(),
                    row.cacheTokens(),
                    row.outputTokens(),
                    row.units(),
                    row.todayRequests(),
                    row.todayFailed(),
                    row.todayTokens(),
                    row.todayUnits());
        }

        private static DailyRouting daily(DailyRoutingRow row) {
            return new DailyRouting(row.day(), row.requests(), row.failed(), row.units());
        }

        private static RecentRequest request(RecentChatRequestRow row) {
            return new RecentRequest(
                    row.requestId(),
                    row.userId(),
                    row.agentSource(),
                    row.routeGroup(),
                    row.provider(),
                    row.account(),
                    row.model(),
                    row.tier(),
                    row.status(),
                    row.errorCode(),
                    row.inputTokens(),
                    row.cacheTokens(),
                    row.outputTokens(),
                    row.totalTokens(),
                    row.units(),
                    row.latencyMs(),
                    row.ttftMs(),
                    row.tps(),
                    row.sessionKey(),
                    row.conversationId(),
                    row.createdAt(),
                    row.completedAt());
        }

        private static AccountActivity account(AccountActivityRow row) {
            return new AccountActivity(
                    row.provider(), row.account(), row.requests(), row.success(), row.failed());
        }
    }

    public record Summary(
            long requests,
            long failed,
            long running,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            BigDecimal units,
            long todayRequests,
            long todayFailed,
            long todayTokens,
            BigDecimal todayUnits) {}

    public record DailyRouting(
            String day,
            long requests,
            long failed,
            BigDecimal units) {}

    public record RecentRequest(
            String requestId,
            String userId,
            String agentSource,
            String routeGroup,
            String provider,
            String account,
            String model,
            String tier,
            String status,
            String errorCode,
            Integer inputTokens,
            Integer cacheTokens,
            Integer outputTokens,
            Integer totalTokens,
            BigDecimal units,
            Integer latencyMs,
            Integer ttftMs,
            BigDecimal tps,
            String sessionKey,
            Long conversationId,
            Instant createdAt,
            Instant completedAt) {}

    public record AccountActivity(
            String provider, String account, long requests, long success, long failed) {}

    public record RoutingUser(
            String userId,
            @Nullable String username,
            @Nullable String displayName,
            long requests,
            long failed,
            @Nullable Instant lastRequestAt) {}

    public record RoutingStats(List<RoutingStatsRow> rows) {}

    public record RoutingHistoryQuery(
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            @Nullable String status,
            @Nullable String provider,
            @Nullable String model,
            @Nullable String agent,
            @Nullable String group) {}

    public record RoutingHistoryPage(
            List<RoutingHistoryItem> items, @Nullable Long nextCursor) {}

    public record RoutingHistoryItem(
            String requestId,
            @Nullable Long conversationId,
            @Nullable String sessionKey,
            @Nullable String sessionTitle,
            String agent,
            @Nullable String mode,
            @Nullable String routeGroup,
            @Nullable String provider,
            @Nullable String account,
            @Nullable String model,
            @Nullable String tier,
            String status,
            long tries,
            @Nullable Integer latencyMs,
            Instant createdAt) {}

    public record RoutingHistoryDetail(
            String requestId,
            @Nullable Long conversationId,
            @Nullable String sessionKey,
            String agent,
            @Nullable String mode,
            @Nullable String routeGroup,
            @Nullable String provider,
            @Nullable String account,
            @Nullable String model,
            @Nullable String tier,
            String status,
            long tries,
            @Nullable Integer latencyMs,
            Instant createdAt,
            @Nullable Instant completedAt,
            @Nullable JsonNode routeTrace,
            @Nullable JsonNode retrievalTrace) {}

    public record PointStats(List<PointStatsRow> rows) {}

    public record ModelStats(List<ModelStatsRow> rows) {}
}
