package cn.tolim.pudans.core.ai.routing;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.plan.UsageUnitCalculator;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RoutePerformanceRow;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RouteCandidateService {

    private static final double DEFAULT_HEALTH = 100;

    private static final Duration PERFORMANCE_WINDOW = Duration.ofHours(1);

    private static final Duration PERFORMANCE_TTL = Duration.ofSeconds(30);

    private final ReactiveExtensionClient client;

    private final SunkRouteRegistry sunkRegistry;

    private final AiStatsRepository statsRepository;

    private volatile PerformanceCache performanceCache = new PerformanceCache(Map.of(), Instant.EPOCH);

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<List<RouteSeat>> expand(AiRouteGroup group) {
        return expand(
                group,
                Set.of(AiModel.Capability.CHAT),
                Set.of(AiModel.Modality.TEXT),
                Set.of(AiModel.Modality.TEXT));
    }

    public Mono<List<RouteSeat>> expand(
            AiRouteGroup group,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities) {
        if (group == null || group.getSpec() == null || !group.getSpec().isEnabled()) {
            return Mono.just(List.of());
        }
        return performance().flatMap(performance -> expand(
                group, capabilities, inputModalities, outputModalities, performance));
    }

    private Mono<List<RouteSeat>> expand(
            AiRouteGroup group,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities,
            Map<String, RoutePerformanceRow> performance) {
        var members = Flux.fromIterable(group.getSpec().getMembers())
                .filter(member -> member != null && member.isEnabled() && StringUtils.hasText(member.getModel()));
        var primary = members
                .concatMap(member -> expandMember(
                        group, member, capabilities, inputModalities, outputModalities, performance))
                .concatMapIterable(seats -> seats);
        // provider fallback 候选始终追加在主候选之后，去重后保持低优先
        var fallback = members
                .concatMap(member -> expandProviderFallback(
                        group, member, capabilities, inputModalities, outputModalities, performance))
                .concatMapIterable(seats -> seats);
        return Flux.concat(primary, fallback).distinct(RouteSeat::key).collectList();
    }

    // 每 30 秒最多查询一次近一小时的实际延迟与吞吐，查询失败时沿用旧快照
    private Mono<Map<String, RoutePerformanceRow>> performance() {
        var now = clock.instant();
        var cached = performanceCache;
        if (Duration.between(cached.loadedAt(), now).compareTo(PERFORMANCE_TTL) < 0) {
            return Mono.just(cached.rows());
        }
        return statsRepository
                .recentRoutePerformance(now.minus(PERFORMANCE_WINDOW))
                .collectMap(RouteCandidateService::performanceKey)
                .doOnNext(rows -> performanceCache = new PerformanceCache(rows, now))
                .onErrorResume(error -> {
                    log.warn("读取路由性能快照失败，沿用上一次结果。", error);
                    return Mono.just(cached.rows());
                });
    }

    private static String performanceKey(RoutePerformanceRow row) {
        return row.provider() + "|" + row.account() + "|" + row.model();
    }

    private Mono<List<RouteSeat>> expandMember(
            AiRouteGroup group,
            AiRouteGroup.Member member,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities,
            Map<String, RoutePerformanceRow> performance) {
        return client.get(AiModel.class, member.getModel())
                .filter(model -> eligible(model, capabilities, inputModalities, outputModalities))
                .flatMap(model -> client.get(AiProvider.class, model.getSpec().getProvider())
                        .filter(RouteCandidateService::isEnabledProvider)
                        .flatMap(provider -> accountsOf(provider, group)
                                .map(account -> seat(group, member, provider, account, model, null, performance))
                                .collectList()))
                .defaultIfEmpty(List.of());
    }

    private Mono<List<RouteSeat>> expandProviderFallback(
            AiRouteGroup group,
            AiRouteGroup.Member member,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities,
            Map<String, RoutePerformanceRow> performance) {
        return client.get(AiModel.class, member.getModel())
                .filter(model -> eligible(model, capabilities, inputModalities, outputModalities))
                .flatMap(model -> client.get(AiProvider.class, model.getSpec().getProvider())
                        .filter(RouteCandidateService::isEnabledProvider)
                        .flatMapMany(provider -> fallbackProviderSeats(
                                group,
                                member,
                                provider,
                                capabilities,
                                inputModalities,
                                outputModalities,
                                performance))
                        .collectList())
                .defaultIfEmpty(List.of());
    }

    private Flux<RouteSeat> fallbackProviderSeats(
            AiRouteGroup group,
            AiRouteGroup.Member member,
            AiProvider provider,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities,
            Map<String, RoutePerformanceRow> performance) {
        var fallback = provider.getSpec() == null ? null : provider.getSpec().getFallback();
        if (fallback == null || fallback.isEmpty()) {
            return Flux.empty();
        }
        var source = provider.getMetadata().getName();
        return Flux.fromIterable(fallback)
                .filter(StringUtils::hasText)
                .distinct()
                .filter(name -> !name.equals(source))
                .concatMap(name -> client.get(AiProvider.class, name)
                        .filter(RouteCandidateService::isEnabledProvider)
                        .flatMapMany(fallbackProvider -> {
                            var options = ListOptions.builder()
                                    .andQuery(equal(
                                            "spec.provider",
                                            fallbackProvider.getMetadata().getName()))
                                    .build();
                            return client.listAll(AiModel.class, options, defaultSort())
                                    .filter(model -> model.getSpec() != null
                                            && model.getSpec().isEnabled()
                                            && model.getSpec().getModelType() == AiModel.ModelType.CHAT)
                                    .filter(model -> eligible(
                                            model, capabilities, inputModalities, outputModalities))
                                    .concatMap(model -> accountsOf(fallbackProvider, group)
                                            .map(account -> seat(
                                                    group,
                                                    member,
                                                    fallbackProvider,
                                                    account,
                                                    model,
                                                    source,
                                                    performance))
                                            .collectList()
                                            .flatMapMany(Flux::fromIterable));
                        }));
    }

    private Flux<AiProviderAccount> accountsOf(AiProvider provider, AiRouteGroup group) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.provider", provider.getMetadata().getName()))
                .build();
        var allowed = group.getSpec().getAccounts();
        return client.listAll(AiProviderAccount.class, options, defaultSort())
                .filter(account -> account.getSpec() != null && account.getSpec().isEnabled())
                .filter(account -> allowed == null
                        || allowed.isEmpty()
                        || allowed.contains(account.getMetadata().getName()))
                .sort(Comparator.comparingInt(account -> account.getSpec().getPriority()));
    }

    private RouteSeat seat(
            AiRouteGroup group,
            AiRouteGroup.Member member,
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            @Nullable String fallbackSource,
            Map<String, RoutePerformanceRow> performance) {
        var modelSpec = model.getSpec();
        var accountSpec = account.getSpec();
        var quota = accountSpec.getQuota();
        Long quotaLimit = quota == null ? null : quota.getLimit();
        Long quotaUsed = quota == null ? null : quota.getUsed();
        Instant resetsAt = quota == null ? null : quota.getResetAt();
        var stats = performance.get(
                provider.getMetadata().getName() + "|"
                        + account.getMetadata().getName() + "|"
                        + model.getMetadata().getName());
        var tier = resolveTier(member, modelSpec);
        return RouteSeat.builder()
                .provider(provider.getMetadata().getName())
                .account(account.getMetadata().getName())
                .model(model.getMetadata().getName())
                .tier(tier)
                .providerOrder(provider.getSpec().getOrder())
                .fallbackSource(fallbackSource)
                .weight(Math.max(1, member.getWeight()) * Math.max(1, accountSpec.getWeight()))
                .free(provider.getSpec().isFree())
                .freePlan(accountSpec.isFreePlan())
                .quotaUsed(quotaUsed)
                .quotaLimit(quotaLimit)
                .resetsAt(resetsAt)
                .pace(pace(quotaLimit, quotaUsed, resetsAt))
                .ttftMillis(stats == null || stats.avgTtftMillis() == null
                        ? 0
                        : stats.avgTtftMillis().max(BigDecimal.ZERO).longValue())
                .tps(stats == null || stats.avgTps() == null ? 0 : stats.avgTps().doubleValue())
                .tokensUsed(stats == null ? 0 : Math.max(0, stats.tokens()))
                .cost(cost(modelSpec, tier))
                .spent(isSpent(quotaLimit, quotaUsed, accountSpec))
                .sunk(sunkRegistry.isSunk(provider.getMetadata().getName(), account.getMetadata().getName()))
                .enabled(true)
                .healthScore(health(provider, account))
                .capabilities(modelSpec.effectiveCapabilities())
                .inputModalities(Set.copyOf(modelSpec.effectiveInputModalities()))
                .outputModalities(Set.copyOf(modelSpec.effectiveOutputModalities()))
                .contextWindow(modelSpec.getContextWindow() == null ? 0 : modelSpec.getContextWindow())
                .maxOutputTokens(modelSpec.getMaxOutputTokens() == null ? 0 : modelSpec.getMaxOutputTokens())
                .extraContextMargin(Math.max(0, member.getExtraContextMargin()))
                .build();
    }

    private static AiModel.Tier resolveTier(AiRouteGroup.Member member, AiModel.Spec modelSpec) {
        return member.getTierOverride() == null ? modelSpec.getTier() : member.getTierOverride();
    }

    private static boolean eligible(
            AiModel model,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities) {
        if (model.getSpec() == null || !model.getSpec().isEnabled()) {
            return false;
        }
        var spec = model.getSpec();
        if (capabilities != null
                && !capabilities.isEmpty()
                && !spec.effectiveCapabilities().containsAll(capabilities)) {
            return false;
        }
        if (inputModalities != null
                && !inputModalities.isEmpty()
                && !spec.effectiveInputModalities().containsAll(inputModalities)) {
            return false;
        }
        return outputModalities == null
                || outputModalities.isEmpty()
                || spec.effectiveOutputModalities().containsAll(outputModalities);
    }

    private static boolean isEnabledProvider(AiProvider provider) {
        return provider.getSpec() != null && provider.getSpec().isEnabled();
    }

    private static boolean isSpent(Long quotaLimit, Long quotaUsed, AiProviderAccount.Spec account) {
        if (quotaLimit != null && quotaLimit > 0 && quotaUsed != null && quotaUsed >= quotaLimit) {
            return true;
        }
        if (account.getWindows() != null
                && account.getWindows().stream().anyMatch(RouteCandidateService::exhaustedWindow)) {
            return true;
        }
        var balance = account.getBalance();
        return balance != null && balance.getLeft() != null && balance.getLeft().signum() <= 0;
    }

    private static boolean exhaustedWindow(AiProviderAccount.QuotaWindow window) {
        if (window == null || window.getLimit() == null || window.getLimit() <= 0
                || window.getUsed() == null || window.getUsed() < window.getLimit()) {
            return false;
        }
        return window.getResetsAt() == null || window.getResetsAt().isAfter(Instant.now());
    }

    private static double health(AiProvider provider, AiProviderAccount account) {
        var providerHealth = provider.getStatus() == null || provider.getStatus().getHealthScore() == null
                ? DEFAULT_HEALTH
                : provider.getStatus().getHealthScore();
        var accountHealth = account.getStatus() == null || account.getStatus().getHealthScore() == null
                ? DEFAULT_HEALTH
                : account.getStatus().getHealthScore();
        return Math.min(providerHealth, accountHealth);
    }

    private double pace(Long quotaLimit, Long quotaUsed, Instant resetsAt) {
        if (quotaLimit == null || quotaLimit <= 0 || resetsAt == null) {
            return 0;
        }
        var now = clock.instant();
        var remainingSeconds = Duration.between(now, resetsAt).getSeconds();
        if (remainingSeconds <= 0) {
            return 0;
        }
        var used = quotaUsed == null ? 0 : quotaUsed;
        var remaining = Math.max(0, quotaLimit - used);
        return remaining * 3600.0 / remainingSeconds;
    }

    private record PerformanceCache(Map<String, RoutePerformanceRow> rows, Instant loadedAt) {}

    // 路由成本按默认计费权重与档位倍率折算，仅用于候选排序
    private static BigDecimal cost(AiModel.Spec modelSpec, AiModel.@Nullable Tier tier) {
        var usage = new AiModelUsage(1, 1, 0, 0, 0, 0, 0, 0, 0, Map.of());
        return new UsageUnitCalculator(new AiSetting.Billing(), false)
                .charge(usage, tier, modelSpec.getModelCode());
    }
}
