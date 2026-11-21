package cn.tolim.pudans.core.ai.gateway;

import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.infra.exception.ApiException;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.UnresolvedModelServerException;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.Nullable;
import org.springframework.web.reactive.function.client.WebClientResponseException;

public final class AiGatewayRetryPolicy {

    private static final int RETRY_LIMIT = 5;

    private final int maxRetries;

    private final long backoffMillis;

    private final List<Integer> retryOnStatuses;

    public AiGatewayRetryPolicy(AiSetting.@Nullable Gateway gateway) {
        var settings = gateway == null ? new AiSetting.Gateway() : gateway;
        this.maxRetries = Math.max(0, Math.min(RETRY_LIMIT, settings.getMaxRetries()));
        this.backoffMillis = Math.max(0, settings.getRetryBackoffMs());
        this.retryOnStatuses = settings.getRetryOnStatuses() == null
                ? List.of()
                : List.copyOf(settings.getRetryOnStatuses());
    }

    public boolean canRetry(int retriesUsed) {
        return retriesUsed < maxRetries;
    }

    public Duration delay(int retriesUsed) {
        var multiplier = 1L << Math.min(Math.max(0, retriesUsed), RETRY_LIMIT);
        return Duration.ofMillis(backoffMillis * multiplier);
    }

    public boolean retryable(@Nullable Throwable error) {
        if (error == null) {
            return false;
        }
        if (error instanceof ApiException api && AiGatewayRateLimiter.CODE_RATE_LIMITED.equals(api.getCode())) {
            return false;
        }
        var status = statusOf(error);
        if (status != null && retryOnStatuses.contains(status)) {
            return true;
        }
        return networkFailure(error);
    }

    public static @Nullable Integer statusOf(@Nullable Throwable error) {
        var httpStatus = httpStatus(error);
        if (httpStatus != null) {
            return httpStatus;
        }
        for (var current = error; current != null; current = nextCause(current)) {
            if (current instanceof RateLimitException) {
                return 429;
            }
            if (current instanceof InternalServerException) {
                return 500;
            }
            if (current instanceof AuthenticationException) {
                return 401;
            }
            if (current instanceof ModelNotFoundException) {
                return 404;
            }
            if (current instanceof InvalidRequestException) {
                return 400;
            }
            if (current instanceof dev.langchain4j.exception.TimeoutException) {
                return 408;
            }
        }
        return null;
    }

    private static @Nullable Integer httpStatus(@Nullable Throwable error) {
        for (var current = error; current != null; current = nextCause(current)) {
            if (current instanceof ApiException api) {
                return api.getStatusCode().value();
            }
            if (current instanceof WebClientResponseException web) {
                return web.getStatusCode().value();
            }
            if (current instanceof HttpException http) {
                return http.statusCode();
            }
        }
        return null;
    }

    private static boolean networkFailure(Throwable error) {
        for (var current = error; current != null; current = nextCause(current)) {
            if (current instanceof IOException
                    || current instanceof TimeoutException
                    || current instanceof dev.langchain4j.exception.TimeoutException
                    || current instanceof UnresolvedModelServerException) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable Throwable nextCause(Throwable error) {
        var cause = error.getCause();
        return cause == null || cause == error ? null : cause;
    }
}