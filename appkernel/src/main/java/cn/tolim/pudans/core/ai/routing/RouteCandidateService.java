package cn.tolim.pudans.core.ai.routing;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RouteCandidateService {

    private static final double DEFAULT_HEALTH = 100;

    private final ReactiveExtensionClient client;

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
        return Flux.fromIterable(group.getSpec().getMembers())
                .filter(member -> member != null && member.isEnabled() && StringUtils.hasText(member.getModel()))
                .concatMap(member -> expandMember(group, member, capabilities, inputModalities, outputModalities))
                .concatMapIterable(seats -> seats)
                .distinct(RouteSeat::key)
                .collectList();
    }

    private Mono<List<RouteSeat>> expandMember(
            AiRouteGroup group,
            AiRouteGroup.Member member,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities) {
        return client.get(AiModel.class, member.getModel())
                .filter(model -> eligible(model, capabilities, inputModalities, outputModalities))
                .flatMap(model -> client.get(AiProvider.class, model.getSpec().getProvider())
                        .filter(RouteCandidateService::isEnabledProvider)
                        .flatMap(provider -> accountsOf(provider)
                                .map(account -> seat(group, member, provider, account, model))
                                .collectList()))
                .defaultIfEmpty(List.of());
    }

    private Flux<AiProviderAccount> accountsOf(AiProvider provider) {
        var options = ListOptions.builder()
                .andQuery(equal("spec.provider", provider.getMetadata().getName()))
                .build();
        return client.listAll(AiProviderAccount.class, options, defaultSort())
                .filter(account -> account.getSpec() != null && account.getSpec().isEnabled())
                .sort(Comparator.comparingInt(account -> account.getSpec().getPriority()));
    }

    private RouteSeat seat(
            AiRouteGroup group,
            AiRouteGroup.Member member,
            AiProvider provider,
            AiProviderAccount account,
            AiModel model) {
        var modelSpec = model.getSpec();
        var accountSpec = account.getSpec();
        var quota = accountSpec.getQuota();
        Long quotaLimit = quota == null ? null : quota.getLimit();
        Long quotaUsed = quota == null ? null : quota.getUsed();
        Instant resetsAt = quota == null ? null : quota.getResetAt();
        return RouteSeat.builder()
                .provider(provider.getMetadata().getName())
                .account(account.getMetadata().getName())
                .model(model.getMetadata().getName())
                .tier(resolveTier(member, modelSpec))
                .weight(Math.max(1, member.getWeight()) * Math.max(1, accountSpec.getWeight()))
                .free(provider.getSpec().isFree())
                .freePlan(accountSpec.isFreePlan())
                .quotaUsed(quotaUsed)
                .quotaLimit(quotaLimit)
                .resetsAt(resetsAt)
                .pace(pace(quotaLimit, quotaUsed, resetsAt))
                .cost(cost(modelSpec))
                .spent(isSpent(quotaLimit, quotaUsed, accountSpec))
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
        var balance = account.getBalance();
        return balance != null && balance.getLeft() != null && balance.getLeft().signum() <= 0;
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

    private static BigDecimal cost(AiModel.Spec modelSpec) {
        var input = BigDecimal.ONE.divide(
                BigDecimal.valueOf(Math.max(1, modelSpec.getInputTokensPerPoint())), 8, RoundingMode.HALF_UP);
        var output = BigDecimal.ONE.divide(
                BigDecimal.valueOf(Math.max(1, modelSpec.getOutputTokensPerPoint())), 8, RoundingMode.HALF_UP);
        return input.add(output);
    }
}
