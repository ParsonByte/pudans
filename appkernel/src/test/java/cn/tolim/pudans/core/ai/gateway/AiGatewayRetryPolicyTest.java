package cn.tolim.pudans.core.ai.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.infra.exception.ApiException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.RateLimitException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

class AiGatewayRetryPolicyTest {

    @Test
    void usesTheDefaultRetrySettingsWhenNoGatewayIsConfigured() {
        var policy = new AiGatewayRetryPolicy(null);

        assertThat(policy.canRetry(0)).isTrue();
        assertThat(policy.canRetry(1)).isTrue();
        assertThat(policy.canRetry(2)).isFalse();
        assertThat(policy.delay(0)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.delay(1)).isEqualTo(Duration.ofMillis(400));
        assertThat(policy.delay(2)).isEqualTo(Duration.ofMillis(800));
    }

    @Test
    void clampsConfiguredValuesToSupportedRanges() {
        var gateway = new AiSetting.Gateway();
        gateway.setMaxRetries(9);
        gateway.setRetryBackoffMs(-10);
        gateway.setRetryOnStatuses(null);

        var policy = new AiGatewayRetryPolicy(gateway);

        assertThat(policy.canRetry(4)).isTrue();
        assertThat(policy.canRetry(5)).isFalse();
        assertThat(policy.delay(0)).isEqualTo(Duration.ZERO);
        assertThat(policy.delay(5)).isEqualTo(Duration.ZERO);
        assertThat(policy.retryable(apiError(HttpStatus.TOO_MANY_REQUESTS))).isFalse();
    }

    @Test
    void retriesOnlyTheConfiguredHttpStatuses() {
        var policy = new AiGatewayRetryPolicy(gateway(2, 200));

        for (var status : List.of(
                HttpStatus.TOO_MANY_REQUESTS,
                HttpStatus.INTERNAL_SERVER_ERROR,
                HttpStatus.BAD_GATEWAY,
                HttpStatus.SERVICE_UNAVAILABLE,
                HttpStatus.GATEWAY_TIMEOUT)) {
            assertThat(policy.retryable(apiError(status))).as(status.toString()).isTrue();
        }
        assertThat(policy.retryable(apiError(HttpStatus.BAD_REQUEST))).isFalse();
        assertThat(policy.retryable(apiError(HttpStatus.UNAUTHORIZED))).isFalse();
        assertThat(policy.retryable(webClientError(503))).isTrue();
        assertThat(policy.retryable(webClientError(400))).isFalse();
    }

    @Test
    void retriesNetworkTimeoutsAndRejectsLocalRateLimits() {
        var policy = new AiGatewayRetryPolicy(gateway(2, 200));

        assertThat(policy.retryable(new RuntimeException("io", new IOException("reset")))).isTrue();
        assertThat(policy.retryable(new RuntimeException("timeout", new SocketTimeoutException("slow"))))
                .isTrue();
        assertThat(policy.retryable(new TimeoutException("timeout"))).isTrue();
        assertThat(policy.retryable(new IllegalStateException("bug"))).isFalse();
        assertThat(policy.retryable(new ApiException(
                HttpStatus.TOO_MANY_REQUESTS, AiGatewayRateLimiter.CODE_RATE_LIMITED, "限流")))
                .isFalse();
        assertThat(policy.retryable(null)).isFalse();
    }

    @Test
    void resolvesTheStatusFromTheCauseChain() {
        var nested = new RuntimeException("outer", apiError(HttpStatus.GATEWAY_TIMEOUT));

        assertThat(AiGatewayRetryPolicy.statusOf(nested)).isEqualTo(504);
        assertThat(AiGatewayRetryPolicy.statusOf(new IllegalStateException("plain"))).isNull();
        assertThat(AiGatewayRetryPolicy.statusOf(null)).isNull();
    }

    @Test
    void retriesLangChain4jProviderFailures() {
        var policy = new AiGatewayRetryPolicy(gateway(2, 200));

        assertThat(AiGatewayRetryPolicy.statusOf(new HttpException(429, "rate"))).isEqualTo(429);
        assertThat(AiGatewayRetryPolicy.statusOf(
                        new RuntimeException("outer", new HttpException(503, "busy"))))
                .isEqualTo(503);
        assertThat(policy.retryable(new HttpException(503, "busy"))).isTrue();
        assertThat(policy.retryable(new HttpException(400, "bad"))).isFalse();
        assertThat(policy.retryable(new RateLimitException("rate"))).isTrue();
        assertThat(policy.retryable(new InternalServerException("busy"))).isTrue();
        assertThat(policy.retryable(new dev.langchain4j.exception.TimeoutException("slow")))
                .isTrue();
        assertThat(policy.retryable(new RuntimeException("nested", new HttpException(502, "gateway"))))
                .isTrue();
    }

    private static AiSetting.Gateway gateway(int maxRetries, int backoffMillis) {
        var gateway = new AiSetting.Gateway();
        gateway.setMaxRetries(maxRetries);
        gateway.setRetryBackoffMs(backoffMillis);
        return gateway;
    }

    private static ApiException apiError(HttpStatus status) {
        return new ApiException(status, "ai-gateway-upstream-error", "上游调用失败。");
    }

    private static WebClientResponseException webClientError(int status) {
        return WebClientResponseException.create(
                status, "error", HttpHeaders.EMPTY, new byte[0], null);
    }
}