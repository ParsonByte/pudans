package cn.tolim.pudans.core.ai.stats;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver.Viewer;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.ContextGridRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.ContextRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.MessageRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.OverviewDailyRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.OverviewRankingRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestDetailRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestTotalsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsDailyRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsHourRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsTotalsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionUsageRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SpeedRow;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiUsageService {

    public static final int DEFAULT_LIMIT = 50;

    public static final int MAX_LIMIT = 200;

    public static final long NO_CURSOR = Long.MAX_VALUE;

    public static final int DEFAULT_GRID_LIMIT = 200;

    public static final int MAX_GRID_LIMIT = 400;

    private static final int RANKING_TOP = 10;

    private static final int TOP_SESSIONS = 10;

    private static final int EXPORT_MAX_ROWS = 10_000;

    private static final int RECENT_MESSAGE_LIMIT = 10;

    private static final int TOKEN_CHARS_PER_UNIT = 4;

    private static final String METRIC_REQUESTS = "requests";

    private static final String METRIC_TOKENS = "tokens";

    private static final String METRIC_POINTS = "points";

    private static final String GROUP_BY_MODEL = "model";

    private static final String GROUP_BY_PROVIDER = "provider";

    private static final DateTimeFormatter HOUR_LABEL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00");

    private static final List<String> MESSAGE_BUCKETS =
            List.of("0", "1-9", "10-19", "20-49", "50-99", "100+");

    private final AiUsageRepository repository;

    private final AiConversationRepository conversationRepository;

    private final AiWalletService walletService;

    private final ReactiveExtensionClient extensionClient;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<Overview> overview(OverviewQuery query, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        var bucket = bucket(query.bucket());
        var team = text(query.team());
        var agent = text(query.agent());
        var provider = text(query.provider());
        var model = text(query.model());
        return Mono.zip(
                        repository
                                .overviewSeries(
                                        window.fromAt(),
                                        window.toAt(),
                                        team,
                                        agent,
                                        provider,
                                        model,
                                        bucket.key(),
                                        viewer.all(),
                                        viewer.userId())
                                .collectList(),
                        repository
                                .overviewRankings(
                                        window.fromAt(),
                                        window.toAt(),
                                        team,
                                        agent,
                                        provider,
                                        model,
                                        viewer.all(),
                                        viewer.userId())
                                .collectList(),
                        walletService.wallet(viewer.userId()))
                .map(tuple -> new Overview(
                        totals(tuple.getT2()),
                        series(window, tuple.getT1(), bucket),
                        rankings(tuple.getT2()),
                        tuple.getT3()));
    }

    public Mono<RequestPage> requests(RequestQuery query, long beforeId, int limit, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        requirePage(beforeId, limit);
        var status = text(query.status());
        var provider = text(query.provider());
        var model = text(query.model());
        var account = text(query.account());
        var agent = text(query.agent());
        var agentSource = text(query.agentSource());
        var mode = text(query.mode());
        var routeGroup = text(query.routeGroup());
        var keyword = text(query.q());
        var purpose = text(query.purpose());
        var via = text(query.via());
        var callerKey = text(query.callerKey());
        var retried = Boolean.TRUE.equals(query.retried());
        return Mono.zip(
                        repository
                                .requests(
                                        window.fromAt(),
                                        window.toAt(),
                                        status,
                                        provider,
                                        model,
                                        account,
                                        agent,
                                        agentSource,
                                        mode,
                                        routeGroup,
                                        keyword,
                                        purpose,
                                        via,
                                        callerKey,
                                        retried,
                                        viewer.all(),
                                        viewer.userId(),
                                        beforeId,
                                        limit + 1)
                                .collectList(),
                        repository.requestTotals(
                                window.fromAt(),
                                window.toAt(),
                                status,
                                provider,
                                model,
                                account,
                                agent,
                                agentSource,
                                mode,
                                routeGroup,
                                keyword,
                                purpose,
                                via,
                                callerKey,
                                retried,
                                viewer.all(),
                                viewer.userId()))
                .map(tuple -> page(tuple.getT1(), limit, totals(tuple.getT2())));
    }

    public Flux<String> export(RequestQuery query, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        return Flux.concat(
                Mono.just(UsageCsvExporter.HEADER),
                repository
                        .requests(
                                window.fromAt(),
                                window.toAt(),
                                text(query.status()),
                                text(query.provider()),
                                text(query.model()),
                                text(query.account()),
                                text(query.agent()),
                                text(query.agentSource()),
                                text(query.mode()),
                                text(query.routeGroup()),
                                text(query.q()),
                                text(query.purpose()),
                                text(query.via()),
                                text(query.callerKey()),
                                Boolean.TRUE.equals(query.retried()),
                                viewer.all(),
                                viewer.userId(),
                                NO_CURSOR,
                                EXPORT_MAX_ROWS)
                        .map(UsageCsvExporter::line));
    }

    public Mono<RequestDetail> request(String requestId, Viewer viewer) {
        var notFound = new ApiException(
                HttpStatus.NOT_FOUND, AiUsageErrorCodes.REQUEST_NOT_FOUND, "请求不存在。");
        return repository
                .requestDetail(requestId)
                .filter(row -> viewer.all() || row.userId().equals(viewer.userId()))
                .switchIfEmpty(Mono.error(notFound))
                .flatMap(row -> providerVendor(row.provider())
                        .map(vendor -> detail(row, vendor))
                        .switchIfEmpty(Mono.fromSupplier(() -> detail(row, null))));
    }

    public Mono<SessionPage> sessions(SessionQuery query, long beforeId, int limit, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        requirePage(beforeId, limit);
        return repository
                .sessions(
                        window.fromAt(),
                        window.toAt(),
                        text(query.agent()),
                        text(query.q()),
                        text(query.provider()),
                        text(query.model()),
                        text(query.status()),
                        text(query.routeGroup()),
                        viewer.all(),
                        viewer.userId(),
                        beforeId,
                        limit + 1)
                .collectList()
                .map(rows -> new SessionPage(
                        rows.stream().limit(limit).map(AiUsageService::session).toList(),
                        rows.size() > limit ? rows.get(limit - 1).conversationId() : null));
    }

    public Mono<SessionDetail> session(long conversationId, Viewer viewer) {
        if (conversationId < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.CONVERSATION_INVALID, "会话 ID 必须大于零。");
        }
        var notFound = new ApiException(
                HttpStatus.NOT_FOUND, AiUsageErrorCodes.SESSION_NOT_FOUND, "会话不存在。");
        return conversationRepository
                .findById(conversationId)
                .filter(conversation -> viewer.all() || conversation.userId().equals(viewer.userId()))
                .switchIfEmpty(Mono.error(notFound))
                .flatMap(conversation -> Mono.zip(
                                repository.sessionUsage(conversationId),
                                repository
                                        .recentMessages(conversationId, RECENT_MESSAGE_LIMIT)
                                        .collectList())
                        .map(tuple -> detail(conversation, tuple.getT1(), tuple.getT2())));
    }

    public Mono<ContextUsage> context(ContextQuery query, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        return Mono.zip(
                        repository
                                .contextRows(
                                        window.fromAt(),
                                        window.toAt(),
                                        text(query.agent()),
                                        viewer.all(),
                                        viewer.userId())
                                .collectList(),
                        extensionClient
                                .listAll(AiModel.class, ListOptions.builder().build(), defaultSort())
                                .collectList(),
                        extensionClient
                                .listAll(AiAgent.class, ListOptions.builder().build(), defaultSort())
                                .collectList())
                .map(tuple -> assembleContext(
                        tuple.getT1(), contextWindows(tuple.getT2()), agentPrompts(tuple.getT3())));
    }

    public Mono<Heatmap> heatmap(HeatmapQuery query, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        var metric = metric(query.metric());
        return repository
                .overviewSeries(
                        window.fromAt(),
                        window.toAt(),
                        "",
                        "",
                        "",
                        "",
                        Bucket.DAY.key(),
                        viewer.all(),
                        viewer.userId())
                .collectList()
                .map(rows -> new Heatmap(
                        metric,
                        window.from().toString(),
                        window.to().toString(),
                        series(window, rows, Bucket.DAY).stream()
                                .map(day -> new HeatmapBucket(
                                        day.day(), null, day.requests(), day.totalTokens(), day.points()))
                                .toList()));
    }

    public Mono<Speed> speed(SpeedQuery query, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        var groupBy = groupBy(query.groupBy());
        return repository
                .speed(window.fromAt(), window.toAt(), groupBy, viewer.all(), viewer.userId())
                .collectList()
                .map(rows -> new Speed(rows.stream().map(AiUsageService::speedItem).toList()));
    }

    public Mono<SessionStats> sessionStats(SessionStatsQuery query, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        var agent = text(query.agent());
        var model = text(query.model());
        return Mono.zip(
                        repository.sessionStatsTotals(
                                window.fromAt(), window.toAt(), agent, model, viewer.all(), viewer.userId()),
                        repository
                                .sessionStatsSessions(
                                        window.fromAt(), window.toAt(), agent, model, viewer.all(), viewer.userId())
                                .collectList(),
                        repository
                                .sessionStatsDaily(
                                        window.fromAt(), window.toAt(), agent, model, viewer.all(), viewer.userId())
                                .collectList(),
                        repository
                                .sessionStatsHours(
                                        window.fromAt(), window.toAt(), agent, model, viewer.all(), viewer.userId())
                                .collectList())
                .map(tuple -> assembleSessionStats(
                        tuple.getT1(), tuple.getT2(), tuple.getT3(), tuple.getT4()));
    }

    public Mono<ContextGrid> contextGrid(GridQuery query, Viewer viewer) {
        var window = UsageWindow.of(query.from(), query.to(), clock);
        var limit = gridLimit(query.limit());
        var agent = text(query.agent());
        var model = text(query.model());
        return Mono.zip(
                        repository
                                .contextGrid(
                                        window.fromAt(),
                                        window.toAt(),
                                        agent,
                                        model,
                                        viewer.all(),
                                        viewer.userId(),
                                        limit)
                                .collectList(),
                        extensionClient
                                .listAll(AiModel.class, ListOptions.builder().build(), defaultSort())
                                .collectList(),
                        extensionClient
                                .listAll(AiAgent.class, ListOptions.builder().build(), defaultSort())
                                .collectList())
                .map(tuple -> new ContextGrid(
                        contextGridItems(
                                tuple.getT1(),
                                contextWindows(tuple.getT2()),
                                agentPrompts(tuple.getT3()))));
    }

    public Mono<ClearResult> clear(ClearQuery query, Viewer viewer) {
        if (!viewer.admin()) {
            return Mono.error(new ApiException(
                    HttpStatus.FORBIDDEN, AiUsageErrorCodes.FORBIDDEN, "仅管理员可以清空用量。"));
        }
        if (query.before() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.BEFORE_REQUIRED, "before 不能为空。"));
        }
        var beforeAt = query.before().atStartOfDay(UsageWindow.SERVER_ZONE).toInstant();
        var provider = text(query.provider());
        var model = text(query.model());
        return Mono.zip(
                        repository.deleteChatRequests(beforeAt, provider, model),
                        repository.deleteModelRequests(beforeAt, provider, model))
                .map(tuple -> new ClearResult(tuple.getT1(), tuple.getT2()));
    }

    private static void requirePage(long beforeId, int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.LIMIT_INVALID, "limit 必须在 1 到 200 之间。");
        }
        if (beforeId < 1) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.CURSOR_INVALID, "beforeId 必须大于零。");
        }
    }

    private static int gridLimit(@Nullable Integer limit) {
        if (limit == null) {
            return DEFAULT_GRID_LIMIT;
        }
        if (limit < 1 || limit > MAX_GRID_LIMIT) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    AiUsageErrorCodes.LIMIT_INVALID,
                    "limit 必须在 1 到 " + MAX_GRID_LIMIT + " 之间。");
        }
        return limit;
    }

    private static String text(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static Bucket bucket(@Nullable String value) {
        var normalized = value == null ? "day" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "hour" -> Bucket.HOUR;
            case "day" -> Bucket.DAY;
            case "week" -> Bucket.WEEK;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    AiUsageErrorCodes.BUCKET_INVALID,
                    "bucket 必须是 hour、day 或 week。");
        };
    }

    private static String metric(@Nullable String value) {
        var normalized = value == null ? METRIC_REQUESTS : value.trim().toLowerCase(Locale.ROOT);
        if (METRIC_REQUESTS.equals(normalized) || METRIC_TOKENS.equals(normalized)
                || METRIC_POINTS.equals(normalized)) {
            return normalized;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                AiUsageErrorCodes.METRIC_INVALID,
                "metric 必须是 requests、tokens 或 points。");
    }

    private static String groupBy(@Nullable String value) {
        var normalized = value == null ? GROUP_BY_MODEL : value.trim().toLowerCase(Locale.ROOT);
        if (GROUP_BY_MODEL.equals(normalized) || GROUP_BY_PROVIDER.equals(normalized)) {
            return normalized;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                AiUsageErrorCodes.GROUP_BY_INVALID,
                "groupBy 必须是 model 或 provider。");
    }

    private static UsageTotals totals(List<OverviewRankingRow> rows) {
        long requests = 0;
        long succeeded = 0;
        long failed = 0;
        long canceled = 0;
        long unpriced = 0;
        long retried = 0;
        long reasoningTokens = 0;
        long inputTokens = 0;
        long cacheTokens = 0;
        long outputTokens = 0;
        long totalTokens = 0;
        long latencySum = 0;
        long latencyCount = 0;
        long ttftSum = 0;
        long ttftCount = 0;
        long tpsCount = 0;
        var points = BigDecimal.ZERO;
        var tpsSum = BigDecimal.ZERO;
        for (var row : rows) {
            requests += row.requests();
            succeeded += row.succeeded();
            failed += row.failed();
            canceled += row.canceledRequests();
            unpriced += row.unpricedRequests();
            retried += row.retriedRequests();
            reasoningTokens += row.reasoningTokens();
            inputTokens += row.inputTokens();
            cacheTokens += row.cacheTokens();
            outputTokens += row.outputTokens();
            totalTokens += row.totalTokens();
            points = points.add(row.points());
            latencySum += row.latencySum();
            latencyCount += row.latencyCount();
            ttftSum += row.ttftSum();
            ttftCount += row.ttftCount();
            tpsSum = tpsSum.add(row.tpsSum());
            tpsCount += row.tpsCount();
        }
        return new UsageTotals(
                requests,
                succeeded,
                failed,
                requests - succeeded - failed,
                inputTokens,
                cacheTokens,
                outputTokens,
                totalTokens,
                points,
                average(latencySum, latencyCount),
                average(ttftSum, ttftCount),
                decimalAverage(tpsSum, tpsCount),
                cacheHitRate(inputTokens, cacheTokens),
                retried,
                canceled,
                unpriced,
                reasoningTokens);
    }

    private static UsageTotals totals(RequestTotalsRow row) {
        return new UsageTotals(
                row.requests(),
                row.succeeded(),
                row.failed(),
                row.requests() - row.succeeded() - row.failed(),
                row.inputTokens(),
                row.cacheTokens(),
                row.outputTokens(),
                row.totalTokens(),
                row.points(),
                null,
                null,
                null,
                cacheHitRate(row.inputTokens(), row.cacheTokens()),
                row.retriedRequests(),
                row.canceledRequests(),
                row.unpricedRequests(),
                row.reasoningTokens());
    }

    static BigDecimal cacheHitRate(@Nullable Integer inputTokens, @Nullable Integer cacheTokens) {
        return cacheHitRate(
                inputTokens == null ? 0 : inputTokens, cacheTokens == null ? 0 : cacheTokens);
    }

    static BigDecimal cacheHitRate(long inputTokens, long cacheTokens) {
        var total = inputTokens + cacheTokens;
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(cacheTokens)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
    }

    static @Nullable String servedEffort(@Nullable String traced, @Nullable String tier) {
        if (StringUtils.hasText(traced)) {
            return traced.trim();
        }
        if (tier == null) {
            return null;
        }
        return switch (tier.trim().toUpperCase(Locale.ROOT)) {
            case "CHEAP" -> "low";
            case "MEDIUM" -> "medium";
            case "HIGH" -> "high";
            case "ULTRA" -> "ultra";
            default -> null;
        };
    }

    private static @Nullable Integer average(long sum, long count) {
        return count == 0 ? null : (int) Math.round((double) sum / count);
    }

    private static @Nullable BigDecimal decimalAverage(BigDecimal sum, long count) {
        return count == 0 ? null : sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    private static List<DailyUsage> series(
            UsageWindow.Window window, List<OverviewDailyRow> rows, Bucket bucket) {
        var byDay = new HashMap<String, OverviewDailyRow>();
        rows.forEach(row -> byDay.put(row.day(), row));
        var series = new ArrayList<DailyUsage>();
        switch (bucket) {
            case HOUR -> {
                var cursor = window.from().atStartOfDay(UsageWindow.SERVER_ZONE);
                var end = window.to().plusDays(1).atStartOfDay(UsageWindow.SERVER_ZONE);
                for (; cursor.isBefore(end); cursor = cursor.plusHours(1)) {
                    series.add(daily(HOUR_LABEL.format(cursor), byDay.get(HOUR_LABEL.format(cursor))));
                }
            }
            case WEEK -> {
                var cursor = window.from().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                while (!cursor.isAfter(window.to())) {
                    series.add(daily(cursor.toString(), byDay.get(cursor.toString())));
                    cursor = cursor.plusWeeks(1);
                }
            }
            case DAY -> {
                for (var day = window.from(); !day.isAfter(window.to()); day = day.plusDays(1)) {
                    series.add(daily(day.toString(), byDay.get(day.toString())));
                }
            }
        }
        return series;
    }

    private static DailyUsage daily(String label, @Nullable OverviewDailyRow row) {
        return row == null
                ? new DailyUsage(label, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO)
                : new DailyUsage(
                        label,
                        row.requests(),
                        row.succeeded(),
                        row.failed(),
                        row.inputTokens(),
                        row.cacheTokens(),
                        row.outputTokens(),
                        row.totalTokens(),
                        row.points());
    }

    private static Rankings rankings(List<OverviewRankingRow> rows) {
        return new Rankings(
                rank(rows, OverviewRankingRow::agent, row -> null),
                rank(rows, OverviewRankingRow::model, row -> null),
                rank(rows, OverviewRankingRow::provider, row -> null),
                rank(rows, OverviewRankingRow::account, OverviewRankingRow::provider));
    }

    private static List<RankingEntry> rank(
            List<OverviewRankingRow> rows,
            Function<OverviewRankingRow, String> name,
            Function<OverviewRankingRow, @Nullable String> provider) {
        var accumulators = new HashMap<String, RankingAccumulator>();
        for (var row : rows) {
            var entryName = name.apply(row);
            var entryProvider = provider.apply(row);
            var key = entryProvider == null ? entryName : entryProvider + '\u0000' + entryName;
            accumulators
                    .computeIfAbsent(key, ignored -> new RankingAccumulator(entryName, entryProvider))
                    .add(row);
        }
        return accumulators.values().stream()
                .sorted(Comparator.comparingLong(RankingAccumulator::requests)
                        .reversed()
                        .thenComparing(RankingAccumulator::points, Comparator.reverseOrder())
                        .thenComparing(RankingAccumulator::name))
                .limit(RANKING_TOP)
                .map(RankingAccumulator::toEntry)
                .toList();
    }

    private static RequestPage page(List<RequestRow> rows, int limit, UsageTotals totals) {
        return new RequestPage(
                rows.stream().limit(limit).map(AiUsageService::request).toList(),
                rows.size() > limit ? rows.get(limit - 1).id() : null,
                totals);
    }

    private static UsageRequest request(RequestRow row) {
        return new UsageRequest(
                row.requestId(),
                row.userId(),
                row.teamId(),
                row.agent(),
                row.agentSource(),
                row.mode(),
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
                row.completedAt(),
                cacheHitRate(row.inputTokens(), row.cacheTokens()),
                row.retried(),
                servedEffort(row.servedEffort(), row.tier()));
    }

    private static RequestDetail detail(RequestDetailRow row, @Nullable String vendor) {
        var trace = row.routeTrace();
        return new RequestDetail(
                row.requestId(),
                row.userId(),
                row.teamId(),
                row.agent(),
                row.agentSource(),
                row.mode(),
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
                new Billing(
                        row.billingSource(),
                        row.billingStatus(),
                        row.overdraft(),
                        row.usageSource(),
                        row.upstreamRequestId()),
                row.latencyMs(),
                row.ttftMs(),
                row.tps(),
                row.sessionKey(),
                row.conversationId(),
                row.routeTrace(),
                row.retrievalTrace(),
                row.createdAt(),
                row.completedAt(),
                retried(trace),
                cacheHitRate(row.inputTokens(), row.cacheTokens()),
                servedEffort(traceEffort(trace), row.tier()),
                vendor,
                stream(trace));
    }

    private Mono<String> providerVendor(@Nullable String provider) {
        if (!StringUtils.hasText(provider)) {
            return Mono.empty();
        }
        return extensionClient
                .get(AiProvider.class, provider)
                .map(item -> item.getSpec() == null ? "" : item.getSpec().getVendor().name())
                .filter(StringUtils::hasText)
                .onErrorResume(ExtensionNotFoundException.class, error -> Mono.empty());
    }

    private static boolean retried(@Nullable JsonNode trace) {
        if (trace == null) {
            return false;
        }
        var tries = trace.get("tries");
        return tries != null && !tries.isNull() && tries.asInt(0) > 1;
    }

    private static @Nullable String traceEffort(@Nullable JsonNode trace) {
        if (trace == null) {
            return null;
        }
        var node = trace.get("servedEffort");
        if (node == null || node.isNull()) {
            node = trace.get("effort");
        }
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.textValue() : node.asText();
    }

    private static @Nullable Boolean stream(@Nullable JsonNode trace) {
        if (trace == null) {
            return null;
        }
        var node = trace.get("stream");
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        var text = node.asText();
        if ("true".equalsIgnoreCase(text)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(text)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static SpeedItem speedItem(SpeedRow row) {
        return new SpeedItem(
                row.name(),
                null,
                row.requests(),
                row.failures(),
                millis(row.avgTtftMs()),
                millis(row.p50TtftMs()),
                rate(row.avgTps()),
                rate(row.p50Tps()));
    }

    private static @Nullable Integer millis(@Nullable Double value) {
        return value == null ? null : (int) Math.round(value);
    }

    private static @Nullable BigDecimal rate(@Nullable Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static SessionStats assembleSessionStats(
            SessionStatsTotalsRow totals,
            List<SessionStatsRow> sessions,
            List<SessionStatsDailyRow> daily,
            List<SessionStatsHourRow> hours) {
        var totalsView = new SessionTotals(
                totals.sessions(),
                round(totals.medianMessages()),
                round(totals.p90Messages()),
                totals.tokens(),
                totals.points(),
                totals.cacheTokens(),
                totals.activeSeconds());
        var series = daily.stream()
                .map(row -> new SessionStatsDay(
                        row.day(),
                        row.sessions(),
                        row.messages(),
                        row.tokens(),
                        row.points(),
                        row.activeSeconds()))
                .toList();
        var hourly = new HashMap<Integer, Long>();
        hours.forEach(row -> hourly.merge(row.hour(), row.sessions(), Long::sum));
        var hourSeries = new ArrayList<SessionStatsHour>();
        for (var hour = 0; hour < 24; hour++) {
            hourSeries.add(new SessionStatsHour(hour, hourly.getOrDefault(hour, 0L)));
        }
        var models = sessionModels(sessions);
        var top = sessions.stream()
                .sorted(Comparator.comparingLong(SessionStatsRow::messageCount)
                        .reversed()
                        .thenComparing(SessionStatsRow::tokens, Comparator.reverseOrder())
                        .thenComparing(SessionStatsRow::lastActivityAt, Comparator.reverseOrder()))
                .limit(TOP_SESSIONS)
                .map(row -> new TopSession(
                        row.conversationId(),
                        row.title(),
                        row.agent(),
                        row.model(),
                        row.messageCount(),
                        row.tokens(),
                        row.points(),
                        row.lastActivityAt()))
                .toList();
        return new SessionStats(totalsView, series, hourSeries, models, top, sessionShape(sessions));
    }

    private static List<SessionModel> sessionModels(List<SessionStatsRow> sessions) {
        var accumulators = new LinkedHashMap<String, ModelAccumulator>();
        for (var row : sessions) {
            var name = row.model() == null ? "unknown" : row.model();
            accumulators.computeIfAbsent(name, ignored -> new ModelAccumulator()).add(row);
        }
        return accumulators.entrySet().stream()
                .map(entry -> new SessionModel(entry.getKey(), entry.getValue().sessions,
                        entry.getValue().tokens, entry.getValue().points))
                .sorted(Comparator.comparingLong(SessionModel::tokens)
                        .reversed()
                        .thenComparing(SessionModel::name))
                .toList();
    }

    private static List<SessionShape> sessionShape(List<SessionStatsRow> sessions) {
        var messages = new LinkedHashMap<String, Long>();
        MESSAGE_BUCKETS.forEach(bucket -> messages.put(bucket, 0L));
        for (var row : sessions) {
            messages.merge(shapeBucket(row.messageCount()), row.messageCount(), Long::sum);
        }
        return messages.entrySet().stream()
                .map(entry -> new SessionShape(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static String shapeBucket(long messages) {
        if (messages <= 0) {
            return "0";
        }
        if (messages <= 9) {
            return "1-9";
        }
        if (messages <= 19) {
            return "10-19";
        }
        if (messages <= 49) {
            return "20-49";
        }
        if (messages <= 99) {
            return "50-99";
        }
        return "100+";
    }

    private static long round(@Nullable Double value) {
        return value == null ? 0 : Math.round(value);
    }

    private static List<ContextGridItem> contextGridItems(
            List<ContextGridRow> rows, Map<String, Integer> windows, Map<String, Long> prompts) {
        return rows.stream()
                .map(row -> contextGridItem(row, windows.get(row.model()), prompts.getOrDefault(row.agent(), 0L)))
                .toList();
    }

    private static ContextGridItem contextGridItem(
            ContextGridRow row, @Nullable Integer contextWindow, long systemTokens) {
        var userTokens = estimateTokens(row.userChars(), row.userCjkChars());
        // 历史 token 取剩余量：输入总量减去已归属的 system/user/memory/kb。
        var historyTokens = Math.max(
                row.inputTokens() - systemTokens - userTokens - row.memoryTokens() - row.kbTokens(), 0);
        var utilization = contextWindow == null || contextWindow <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(row.inputTokens())
                        .divide(BigDecimal.valueOf(contextWindow), 4, RoundingMode.HALF_UP);
        return new ContextGridItem(
                row.requestId(),
                row.agent(),
                row.model(),
                row.createdAt(),
                row.inputTokens(),
                row.outputTokens(),
                contextWindow,
                utilization,
                new ContextParts(
                        systemTokens,
                        historyTokens,
                        row.memoryTokens(),
                        row.kbTokens(),
                        userTokens,
                        row.outputTokens()));
    }

    private static UsageSession session(SessionRow row) {
        return new UsageSession(
                row.conversationId(),
                row.title(),
                row.userId(),
                row.teamId(),
                row.agent(),
                row.agentSource(),
                row.status(),
                row.model(),
                row.messageCount(),
                row.requestCount(),
                row.inputTokens(),
                row.cacheTokens(),
                row.outputTokens(),
                row.totalTokens(),
                row.points(),
                duration(row.createdAt(), row.lastActivityAt()),
                row.createdAt(),
                row.lastActivityAt(),
                cacheHitRate(row.inputTokens(), row.cacheTokens()));
    }

    private static SessionDetail detail(
            Conversation conversation, SessionUsageRow usage, List<MessageRow> messages) {
        return new SessionDetail(
                new UsageSession(
                        conversation.id(),
                        conversation.title(),
                        conversation.userId(),
                        conversation.teamId(),
                        conversation.agentId(),
                        conversation.agentSource(),
                        conversation.status(),
                        usage.model(),
                        usage.messageCount(),
                        usage.requestCount(),
                        usage.inputTokens(),
                        usage.cacheTokens(),
                        usage.outputTokens(),
                        usage.totalTokens(),
                        usage.points(),
                        duration(conversation.createdAt(), conversation.lastActivityAt()),
                        conversation.createdAt(),
                        conversation.lastActivityAt(),
                        cacheHitRate(usage.inputTokens(), usage.cacheTokens())),
                usage.messageCount(),
                usage.userMessageCount(),
                usage.assistantMessageCount(),
                messages.stream()
                        .map(message -> new RecentMessage(
                                message.messageId(),
                                message.role(),
                                message.content(),
                                message.tokenCount(),
                                message.model(),
                                message.createdAt()))
                        .toList());
    }

    private static long duration(Instant createdAt, Instant lastActivityAt) {
        return Math.max(Duration.between(createdAt, lastActivityAt).toSeconds(), 0);
    }

    private static ContextUsage assembleContext(
            List<ContextRow> rows, Map<String, Integer> windows, Map<String, Long> prompts) {
        var models = new LinkedHashMap<String, ContextAccumulator>();
        var agents = new LinkedHashMap<String, ContextAccumulator>();
        var totals = new ContextAccumulator();
        for (var row : rows) {
            var systemTokens = prompts.getOrDefault(row.agent(), 0L) * row.requests();
            var userTokens = estimateTokens(row.userChars(), row.userCjkChars());
            // 历史 token 取剩余量：提示总量减去已归属的 system/user/memory/kb。
            var historyTokens = Math.max(
                    row.inputTokens() - systemTokens - userTokens - row.memoryTokens() - row.kbTokens(), 0);
            var windowSize = windows.get(row.model());
            models.computeIfAbsent(row.model(), ignored -> new ContextAccumulator(row.model(), windowSize))
                    .add(row, systemTokens, historyTokens, userTokens, windowSize);
            agents.computeIfAbsent(row.agent(), ignored -> new ContextAccumulator(row.agent(), null))
                    .add(row, systemTokens, historyTokens, userTokens, windowSize);
            totals.add(row, systemTokens, historyTokens, userTokens, windowSize);
        }
        return new ContextUsage(
                totals.toTotals(),
                agents.values().stream().map(ContextAccumulator::toBreakdown).toList(),
                models.values().stream().map(ContextAccumulator::toBreakdown).toList());
    }

    private static Map<String, Integer> contextWindows(List<AiModel> models) {
        var windows = new HashMap<String, Integer>();
        for (var model : models) {
            var spec = model.getSpec();
            if (spec != null
                    && spec.getModelCode() != null
                    && spec.getContextWindow() != null
                    && spec.getContextWindow() > 0) {
                windows.putIfAbsent(spec.getModelCode(), spec.getContextWindow());
            }
        }
        return windows;
    }

    private static Map<String, Long> agentPrompts(List<AiAgent> agents) {
        var prompts = new HashMap<String, Long>();
        for (var agent : agents) {
            var spec = agent.getSpec();
            if (spec != null && spec.getPrompt() != null && !spec.getPrompt().isBlank()) {
                prompts.put(agent.getMetadata().getName(), estimateTokens(spec.getPrompt()));
            }
        }
        return prompts;
    }

    private static long estimateTokens(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        long total = 0;
        long cjk = 0;
        for (var index = 0; index < value.length(); ) {
            var codePoint = value.codePointAt(index);
            index += Character.charCount(codePoint);
            total++;
            if (isCjk(codePoint)) {
                cjk++;
            }
        }
        return estimateTokens(total, cjk);
    }

    // 字符估算口径：CJK 每字 1 token，其余字符每 4 个 1 token。
    private static long estimateTokens(long totalChars, long cjkChars) {
        var other = Math.max(totalChars - cjkChars, 0);
        return cjkChars + (other + TOKEN_CHARS_PER_UNIT - 1) / TOKEN_CHARS_PER_UNIT;
    }

    private static boolean isCjk(int codePoint) {
        var script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL;
    }

    private enum Bucket {
        HOUR("hour"),
        DAY("day"),
        WEEK("week");

        private final String key;

        Bucket(String key) {
            this.key = key;
        }

        private String key() {
            return key;
        }
    }

    private static final class RankingAccumulator {

        private final String name;

        private final @Nullable String provider;

        private long requests;

        private long failed;

        private long inputTokens;

        private long cacheTokens;

        private long outputTokens;

        private BigDecimal points = BigDecimal.ZERO;

        private RankingAccumulator(String name, @Nullable String provider) {
            this.name = name;
            this.provider = provider;
        }

        private void add(OverviewRankingRow row) {
            requests += row.requests();
            failed += row.failed();
            inputTokens += row.inputTokens();
            cacheTokens += row.cacheTokens();
            outputTokens += row.outputTokens();
            points = points.add(row.points());
        }

        private long requests() {
            return requests;
        }

        private BigDecimal points() {
            return points;
        }

        private String name() {
            return name;
        }

        private RankingEntry toEntry() {
            return new RankingEntry(
                    name, provider, requests, failed, inputTokens, cacheTokens, outputTokens, points);
        }
    }

    private static final class ModelAccumulator {

        private long sessions;

        private long tokens;

        private BigDecimal points = BigDecimal.ZERO;

        private void add(SessionStatsRow row) {
            sessions++;
            tokens += row.tokens();
            points = points.add(row.points());
        }
    }

    private static final class ContextAccumulator {

        private final String name;

        private final @Nullable Integer contextWindow;

        private long requests;

        private long inputTokens;

        private long outputTokens;

        private long systemTokens;

        private long historyTokens;

        private long memoryTokens;

        private long kbTokens;

        private long userTokens;

        private long windowTokens;

        private long windowInputTokens;

        private ContextAccumulator() {
            this(null, null);
        }

        private ContextAccumulator(String name, @Nullable Integer contextWindow) {
            this.name = name;
            this.contextWindow = contextWindow;
        }

        private void add(
                ContextRow row,
                long system,
                long history,
                long user,
                @Nullable Integer windowSize) {
            requests += row.requests();
            inputTokens += row.inputTokens();
            outputTokens += row.outputTokens();
            systemTokens += system;
            historyTokens += history;
            memoryTokens += row.memoryTokens();
            kbTokens += row.kbTokens();
            userTokens += user;
            if (windowSize != null && windowSize > 0) {
                windowTokens += (long) windowSize * row.requests();
                windowInputTokens += row.inputTokens();
            }
        }

        private ContextTotals toTotals() {
            return new ContextTotals(
                    requests,
                    inputTokens,
                    outputTokens,
                    systemTokens,
                    historyTokens,
                    memoryTokens,
                    kbTokens,
                    userTokens,
                    windowTokens == 0 ? null : windowTokens,
                    utilization());
        }

        private ContextBreakdown toBreakdown() {
            return new ContextBreakdown(
                    name,
                    contextWindow,
                    requests,
                    inputTokens,
                    outputTokens,
                    systemTokens,
                    historyTokens,
                    memoryTokens,
                    kbTokens,
                    userTokens,
                    windowTokens == 0 ? null : windowTokens,
                    utilization());
        }

        private @Nullable BigDecimal utilization() {
            if (windowTokens == 0) {
                return null;
            }
            return BigDecimal.valueOf(windowInputTokens)
                    .divide(BigDecimal.valueOf(windowTokens), 4, RoundingMode.HALF_UP);
        }
    }

    public record OverviewQuery(
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            @Nullable String team,
            @Nullable String agent,
            @Nullable String provider,
            @Nullable String model,
            @Nullable String bucket) {}

    public record RequestQuery(
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            @Nullable String status,
            @Nullable String provider,
            @Nullable String model,
            @Nullable String account,
            @Nullable String agent,
            @Nullable String agentSource,
            @Nullable String mode,
            @Nullable String routeGroup,
            @Nullable String q,
            @Nullable String purpose,
            @Nullable String via,
            @Nullable String callerKey,
            @Nullable Boolean retried) {}

    public record SessionQuery(
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            @Nullable String agent,
            @Nullable String q,
            @Nullable String provider,
            @Nullable String model,
            @Nullable String status,
            @Nullable String routeGroup) {}

    public record ContextQuery(
            @Nullable LocalDate from, @Nullable LocalDate to, @Nullable String agent) {}

    public record HeatmapQuery(
            @Nullable LocalDate from, @Nullable LocalDate to, @Nullable String metric) {}

    public record SpeedQuery(
            @Nullable LocalDate from, @Nullable LocalDate to, @Nullable String groupBy) {}

    public record SessionStatsQuery(
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            @Nullable String agent,
            @Nullable String model) {}

    public record GridQuery(
            @Nullable LocalDate from,
            @Nullable LocalDate to,
            @Nullable String agent,
            @Nullable String model,
            @Nullable Integer limit) {}

    public record ClearQuery(
            @Nullable LocalDate before, @Nullable String provider, @Nullable String model) {}

    public record Overview(
            UsageTotals totals, List<DailyUsage> series, Rankings rankings, AiWalletService.WalletDetail quotas) {}

    public record UsageTotals(
            long requests,
            long succeeded,
            long failed,
            long running,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal points,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            BigDecimal cacheHitRate,
            long retriedRequests,
            long canceledRequests,
            long unpricedRequests,
            long reasoningTokens) {}

    public record DailyUsage(
            String day,
            long requests,
            long succeeded,
            long failed,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal points) {}

    public record Rankings(
            List<RankingEntry> agents,
            List<RankingEntry> models,
            List<RankingEntry> providers,
            List<RankingEntry> accounts) {}

    public record RankingEntry(
            String name,
            @Nullable String provider,
            long requests,
            long failed,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            BigDecimal points) {}

    public record RequestPage(
            List<UsageRequest> items, @Nullable Long nextCursor, UsageTotals totals) {}

    public record UsageRequest(
            String requestId,
            String userId,
            String teamId,
            String agent,
            String agentSource,
            @Nullable String mode,
            @Nullable String routeGroup,
            @Nullable String provider,
            @Nullable String account,
            @Nullable String model,
            @Nullable String tier,
            String status,
            @Nullable String errorCode,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer totalTokens,
            @Nullable BigDecimal points,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String sessionKey,
            @Nullable Long conversationId,
            Instant createdAt,
            @Nullable Instant completedAt,
            BigDecimal cacheHitRate,
            boolean retried,
            @Nullable String servedEffort) {}

    public record RequestDetail(
            String requestId,
            String userId,
            String teamId,
            String agent,
            String agentSource,
            @Nullable String mode,
            @Nullable String routeGroup,
            @Nullable String provider,
            @Nullable String account,
            @Nullable String model,
            @Nullable String tier,
            String status,
            @Nullable String errorCode,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer totalTokens,
            @Nullable BigDecimal points,
            Billing billing,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String sessionKey,
            @Nullable Long conversationId,
            @Nullable JsonNode routeTrace,
            @Nullable JsonNode retrievalTrace,
            Instant createdAt,
            @Nullable Instant completedAt,
            boolean retried,
            BigDecimal cacheHitRate,
            @Nullable String servedEffort,
            @Nullable String vendor,
            @Nullable Boolean stream) {}

    public record Billing(
            @Nullable String source,
            String status,
            @Nullable BigDecimal overdraft,
            @Nullable String usageSource,
            @Nullable String upstreamRequestId) {}

    public record SessionPage(List<UsageSession> items, @Nullable Long nextCursor) {}

    public record UsageSession(
            long conversationId,
            @Nullable String title,
            String userId,
            String teamId,
            String agent,
            String agentSource,
            String status,
            @Nullable String model,
            long messageCount,
            long requestCount,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal points,
            long durationSeconds,
            Instant createdAt,
            Instant lastActivityAt,
            BigDecimal cacheHitRate) {}

    public record SessionDetail(
            UsageSession conversation,
            long messageCount,
            long userMessageCount,
            long assistantMessageCount,
            List<RecentMessage> recentMessages) {}

    public record RecentMessage(
            long messageId,
            String role,
            String content,
            @Nullable Integer tokens,
            @Nullable String model,
            Instant createdAt) {}

    public record ContextUsage(
            ContextTotals totals, List<ContextBreakdown> agents, List<ContextBreakdown> models) {}

    public record ContextTotals(
            long requests,
            long inputTokens,
            long outputTokens,
            long systemTokens,
            long historyTokens,
            long memoryTokens,
            long kbTokens,
            long userTokens,
            @Nullable Long contextWindowTokens,
            @Nullable BigDecimal windowUtilization) {}

    public record ContextBreakdown(
            String name,
            @Nullable Integer contextWindow,
            long requests,
            long inputTokens,
            long outputTokens,
            long systemTokens,
            long historyTokens,
            long memoryTokens,
            long kbTokens,
            long userTokens,
            @Nullable Long contextWindowTokens,
            @Nullable BigDecimal windowUtilization) {}

    public record Heatmap(
            String metric, String start, String end, List<HeatmapBucket> buckets) {}

    public record HeatmapBucket(
            String date, @Nullable Integer hour, long requests, long tokens, BigDecimal points) {}

    public record Speed(List<SpeedItem> items) {}

    public record SpeedItem(
            String name,
            @Nullable String provider,
            long requests,
            long failures,
            @Nullable Integer avgTtftMs,
            @Nullable Integer p50TtftMs,
            @Nullable BigDecimal avgTps,
            @Nullable BigDecimal p50Tps) {}

    public record SessionStats(
            SessionTotals totals,
            List<SessionStatsDay> series,
            List<SessionStatsHour> hours,
            List<SessionModel> models,
            List<TopSession> topSessions,
            List<SessionShape> shape) {}

    public record SessionTotals(
            long sessions,
            long medianMessages,
            long p90Messages,
            long tokens,
            BigDecimal points,
            long cacheReadTokens,
            long activeSeconds) {}

    public record SessionStatsDay(
            String day,
            long sessions,
            long messages,
            long tokens,
            BigDecimal points,
            long activeSeconds) {}

    public record SessionStatsHour(int hour, long sessions) {}

    public record SessionModel(String name, long sessions, long tokens, BigDecimal points) {}

    public record TopSession(
            long conversationId,
            @Nullable String title,
            String agent,
            @Nullable String model,
            long messages,
            long tokens,
            BigDecimal points,
            Instant lastActivityAt) {}

    public record SessionShape(String bucket, long messages) {}

    public record ContextGrid(List<ContextGridItem> items) {}

    public record ContextGridItem(
            String requestId,
            String agent,
            String model,
            Instant createdAt,
            long inputTokens,
            long outputTokens,
            @Nullable Integer contextWindow,
            BigDecimal utilization,
            ContextParts parts) {}

    public record ContextParts(
            long system, long history, long memory, long kb, long user, long output) {}

    public record ClearResult(long requestsDeleted, long modelRequestsDeleted) {}
}