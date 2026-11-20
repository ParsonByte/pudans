package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.PersonalAccessToken;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiGatewayKeyService {

    public static final String CODE_KEY_DENIED = "ai-gateway-key-denied";

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final ReactiveExtensionClient client;

    private final AiChatRequestRepository chatRequestRepository;

    private final AiGatewayRateLimiter rateLimiter;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<Access> authorize(AiGatewayInvocation invocation, AiModel model) {
        var callerKey = invocation.callerKey();
        if (!StringUtils.hasText(callerKey)) {
            return Mono.just(Access.unrestricted());
        }
        return pat(callerKey)
                .flatMap(pat -> {
                    var spec = pat.getSpec();
                    var gateway = spec == null || spec.getGateway() == null
                            ? new PersonalAccessToken.Gateway()
                            : spec.getGateway();
                    if (!gateway.isEnabled()) {
                        return Mono.error(denied("调用密钥的网关访问已停用。"));
                    }
                    if (!modelAllowed(gateway.getModels(), model)) {
                        return Mono.error(denied("调用密钥不允许使用模型 " + modelCode(model) + "。"));
                    }
                    return checkLimits(callerKey, gateway.getLimits())
                            .thenReturn(new Access(
                                    callerKey, gateway.getModels(), gateway.getMaxConcurrency()));
                });
    }

    public Mono<AiGatewayRateLimiter.Permit> acquire(Access access) {
        if (access == null || access.callerKey() == null) {
            return Mono.just(AiGatewayRateLimiter.Permit.empty());
        }
        return rateLimiter.acquireKey(access.callerKey(), access.maxConcurrency());
    }

    public Mono<Void> release(AiGatewayRateLimiter.Permit permit) {
        return rateLimiter.release(permit);
    }

    private Mono<PersonalAccessToken> pat(String callerKey) {
        return client.get(PersonalAccessToken.class, callerKey)
                .switchIfEmpty(Mono.error(denied("调用密钥不存在。")))
                .onErrorMap(
                        ExtensionNotFoundException.class,
                        error -> denied("调用密钥不存在。"));
    }

    private Mono<Void> checkLimits(String callerKey, List<PersonalAccessToken.Limit> limits) {
        if (limits == null || limits.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(limits)
                .filter(Objects::nonNull)
                .concatMap(limit -> chatRequestRepository
                        .aggregateKeyUsage(
                                null,
                                callerKey,
                                since(limit.getPeriod()),
                                limit.getModels(),
                                limit.getAccount())
                        .flatMap(usage -> exceeded(limit, usage)
                                ? Mono.error(limited(limit))
                                : Mono.empty()))
                .then();
    }

    private static boolean exceeded(PersonalAccessToken.Limit limit, AiChatRequestRepository.KeyUsage usage) {
        if (limit.getTokens() != null && usage.tokens() >= limit.getTokens()) {
            return true;
        }
        if (limit.getPoints() != null
                && usage.points() != null
                && usage.points().compareTo(BigDecimal.valueOf(limit.getPoints())) >= 0) {
            return true;
        }
        return limit.getRequests() != null && usage.requests() >= limit.getRequests();
    }

    private static boolean modelAllowed(
            @Nullable List<String> models, @Nullable AiModel model) {
        if (models == null || models.isEmpty()) {
            return true;
        }
        if (model == null) {
            return false;
        }
        var name = model.getMetadata() == null ? null : model.getMetadata().getName();
        var code = modelCode(model);
        return models.stream()
                .filter(StringUtils::hasText)
                .anyMatch(allowed -> allowed.equalsIgnoreCase(name) || allowed.equalsIgnoreCase(code));
    }

    private static String modelCode(@Nullable AiModel model) {
        return model == null || model.getSpec() == null || !StringUtils.hasText(model.getSpec().getModelCode())
                ? ""
                : model.getSpec().getModelCode();
    }

    private @Nullable Instant since(PersonalAccessToken.@Nullable LimitPeriod period) {
        var now = ZonedDateTime.now(clock).withZoneSameInstant(ZONE);
        return switch (period == null ? PersonalAccessToken.LimitPeriod.DAY : period) {
            case DAY -> now.toLocalDate().atStartOfDay(ZONE).toInstant();
            case WEEK -> now.toLocalDate()
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .atStartOfDay(ZONE)
                    .toInstant();
            case MONTH -> now.toLocalDate().withDayOfMonth(1).atStartOfDay(ZONE).toInstant();
            case TOTAL -> null;
        };
    }

    private static ApiException denied(String reason) {
        return new ApiException(HttpStatus.FORBIDDEN, CODE_KEY_DENIED, reason);
    }

    private static ApiException limited(PersonalAccessToken.Limit limit) {
        var period = limit.getPeriod() == null ? PersonalAccessToken.LimitPeriod.DAY : limit.getPeriod();
        return new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                AiGatewayRateLimiter.CODE_KEY_LIMITED,
                "调用密钥已超过 " + period.name() + " 预算限制。");
    }

    public record Access(
            @Nullable String callerKey, List<String> models, @Nullable Integer maxConcurrency) {

        public Access {
            models = models == null ? List.of() : List.copyOf(models);
        }

        public static Access unrestricted() {
            return new Access(null, List.of(), null);
        }

        public boolean allows(@Nullable String model) {
            if (callerKey == null || models.isEmpty()) {
                return true;
            }
            return model != null && models.stream()
                    .filter(StringUtils::hasText)
                    .anyMatch(allowed -> allowed.equalsIgnoreCase(model));
        }
    }
}