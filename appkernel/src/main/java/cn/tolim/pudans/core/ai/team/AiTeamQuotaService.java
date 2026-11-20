package cn.tolim.pudans.core.ai.team;

import cn.tolim.pudans.contract.core.extension.ai.AiTeam;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 团队配额按滚动窗口统计已结算的标准 token 单位，超出后拒绝新的计费请求
@Service
@RequiredArgsConstructor
public class AiTeamQuotaService {

    public static final String CODE_QUOTA_EXCEEDED = "ai-team-quota-exceeded";

    private final ReactiveExtensionClient client;

    private final AiStatsRepository statsRepository;

    public Mono<Void> check(String teamId, BigDecimal estimatedUnits) {
        if (!StringUtils.hasText(teamId)) {
            return Mono.empty();
        }
        var estimate = estimatedUnits == null ? BigDecimal.ZERO : estimatedUnits.max(BigDecimal.ZERO);
        return client.get(AiTeam.class, teamId)
                .flatMap(team -> {
                    var quota = team.getSpec() == null ? null : team.getSpec().getQuota();
                    if (quota == null || quota.getLimit() == null || quota.getLimit().signum() <= 0) {
                        return Mono.empty();
                    }
                    var now = Instant.now();
                    var windowDays = Math.max(1, quota.getWindowDays());
                    var since = quota.getResetsAt() != null && quota.getResetsAt().isAfter(now)
                            ? quota.getResetsAt().minus(Duration.ofDays(windowDays))
                            : now.minus(Duration.ofDays(windowDays));
                    return statsRepository.teamUnitsUsed(teamId, since).flatMap(used -> {
                        if (used.add(estimate).compareTo(quota.getLimit()) > 0) {
                            return Mono.error(new ApiException(
                                    HttpStatus.PAYMENT_REQUIRED,
                                    CODE_QUOTA_EXCEEDED,
                                    "团队额度已用尽。"));
                        }
                        return Mono.empty();
                    });
                })
                .onErrorResume(ExtensionNotFoundException.class, error -> Mono.empty())
                .then();
    }
}