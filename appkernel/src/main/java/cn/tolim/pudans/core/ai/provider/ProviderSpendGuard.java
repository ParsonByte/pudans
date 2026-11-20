package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

// 供应商账号级花费上限与配额窗口守卫
@Service
@RequiredArgsConstructor
public class ProviderSpendGuard {

    public static final String CODE_SPEND_CAP = "ai-provider-spend-cap";

    public static final String CODE_WINDOW_EXHAUSTED = "ai-provider-quota-window-exhausted";

    private final AiStatsRepository statsRepository;

    public Mono<Void> check(
            AiProvider provider, AiProviderAccount account, BigDecimal estimatedPoints) {
        if (provider == null || account == null || account.getSpec() == null) {
            return Mono.empty();
        }
        var exhaustedWindow = account.getSpec().getWindows().stream()
                .filter(window -> window != null
                        && window.getLimit() != null
                        && window.getLimit() > 0
                        && window.getUsed() != null
                        && window.getUsed() >= window.getLimit()
                        && (window.getResetsAt() == null || window.getResetsAt().isAfter(Instant.now())))
                .findFirst();
        if (exhaustedWindow.isPresent()) {
            return Mono.error(new ApiException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    CODE_WINDOW_EXHAUSTED,
                    "供应商账号配额窗口已用尽：" + name(exhaustedWindow.get().getName()) + "。"));
        }
        var cap = account.getSpec().getSpendCapPoints();
        if (cap == null || cap <= 0) {
            return Mono.empty();
        }
        var estimate = estimatedPoints == null ? BigDecimal.ZERO : estimatedPoints.max(BigDecimal.ZERO);
        var providerName = provider.getMetadata().getName();
        var accountName = account.getMetadata().getName();
        return statsRepository.providerAccountSpent(providerName, accountName).flatMap(spent -> {
            if (spent.add(estimate).compareTo(BigDecimal.valueOf(cap)) > 0) {
                return Mono.error(new ApiException(
                        HttpStatus.PAYMENT_REQUIRED,
                        CODE_SPEND_CAP,
                        "供应商账号累计花费已达到上限。"));
            }
            return Mono.empty();
        });
    }

    private static String name(String value) {
        return value == null || value.isBlank() ? "default" : value;
    }
}