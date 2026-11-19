package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository.ModelStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.AccountActivityRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.DailyRoutingRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.KnowledgeBaseStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.MemoryStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.PointStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RecentChatRequestRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RoutingStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RoutingSummaryRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.UserActivityRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiStatsService {

    private final AiStatsRepository repository;

    private final AiModelRequestRepository modelRequestRepository;

    private final UserService userService;

    public Mono<RoutingStats> routing(int days) {
        return repository.routing(days(days)).collectList().map(RoutingStats::new);
    }

    public Mono<PointStats> points(int days) {
        return repository.points(days(days)).collectList().map(PointStats::new);
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
                    row.points(),
                    row.todayRequests(),
                    row.todayFailed(),
                    row.todayTokens(),
                    row.todayPoints());
        }

        private static DailyRouting daily(DailyRoutingRow row) {
            return new DailyRouting(row.day(), row.requests(), row.failed(), row.points());
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
                    row.points(),
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
            BigDecimal points,
            long todayRequests,
            long todayFailed,
            long todayTokens,
            BigDecimal todayPoints) {}

    public record DailyRouting(String day, long requests, long failed, BigDecimal points) {}

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
            BigDecimal points,
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

    public record PointStats(List<PointStatsRow> rows) {}

    public record ModelStats(List<ModelStatsRow> rows) {}
}
