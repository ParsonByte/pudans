package cn.tolim.pudans.core.webhook;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.ExtensionUtil.notDeleting;
import static java.nio.charset.StandardCharsets.UTF_8;

import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Slf4j
@Component
public class WebhookDispatcher implements InitializingBean, DisposableBean {

    private static final int CONCURRENCY = 4;

    private static final Duration RETRY_INTERVAL = Duration.ofMillis(500);

    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

    private static final String STATUS_SUCCESS = "SUCCESS";

    private static final String STATUS_FAILURE = "FAILURE";

    private final WebhookEventBus eventBus;

    private final ReactiveExtensionClient client;

    private final WebhookSecretCipher secretCipher;

    private final AuditLog auditLog;

    private final OutboundRequestValidator outboundRequestValidator;

    private final WebClient webClient;

    public WebhookDispatcher(
            WebhookEventBus eventBus,
            ReactiveExtensionClient client,
            WebhookSecretCipher secretCipher,
            AuditLog auditLog,
            OutboundRequestValidator outboundRequestValidator) {
        this.eventBus = eventBus;
        this.client = client;
        this.secretCipher = secretCipher;
        this.auditLog = auditLog;
        this.outboundRequestValidator = outboundRequestValidator;
        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                        .resolver(new ValidatingAddressResolverGroup(outboundRequestValidator))
                        .followRedirect(false)
                        .responseTimeout(Duration.ofSeconds(30))))
                .build();
    }

    private volatile CountDownLatch drained;

    private Disposable subscription;

    private static String payload(WebhookEvent event) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("id", event.id());
        payload.put("type", event.type());
        payload.put("occurredAt", event.occurredAt().toString());
        payload.put("data", event.data());
        return JsonUtils.objectToJson(payload);
    }

    static String sign(String secret, String timestamp, String body) {
        if (!org.springframework.util.StringUtils.hasText(secret)) {
            return null;
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(UTF_8), "HmacSHA256"));
            var digest = mac.doFinal((timestamp + '.' + body).getBytes(UTF_8));
            var hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return "sha256=" + hex;
        } catch (Exception e) {
            throw new IllegalStateException("无法对 Webhook 载荷进行签名", e);
        }
    }

    private static boolean retryable(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError() || response.getStatusCode().value() == 429;
        }
        return error instanceof WebClientRequestException
                || error instanceof TimeoutException
                || error instanceof java.io.IOException;
    }

    private static int statusOf(Throwable error) {
        return error instanceof WebClientResponseException response
                ? response.getStatusCode().value()
                : 0;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    @Override
    public void afterPropertiesSet() {
        var latch = new CountDownLatch(1);
        this.drained = latch;
        subscription = eventBus
                .events()
                .flatMap(this::dispatch, CONCURRENCY)
                .doOnError(error -> log.warn("Webhook 分发器发生错误", error))
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofMinutes(1))
                        .doBeforeRetry(signal -> log.warn(
                                "发生错误后重新订阅 Webhook 分发器", signal.failure())))
                .doFinally(signal -> latch.countDown())
                .subscribe();
    }

    @Override
    public void destroy() {
        eventBus.complete();
        var latch = this.drained;
        if (latch != null) {
            try {
                if (!latch.await(SHUTDOWN_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                    log.warn("排空 Webhook 事件总线时超时");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        var current = subscription;
        if (current != null) {
            current.dispose();
        }
    }

    private Mono<Void> dispatch(WebhookEvent event) {
        return client
                .listAll(Webhook.class, ListOptions.builder().andQuery(notDeleting()).build(), defaultSort())
                .filter(webhook -> webhook.getSpec() != null && webhook.getSpec().isEnabled())
                .filter(webhook -> webhook.subscribes(event.type()))
                .concatMap(webhook -> deliver(webhook, event))
                .then()
                .onErrorResume(error -> {
                    log.warn("无法分发 Webhook 事件 [{}]", event.type(), error);
                    return Mono.empty();
                });
    }

    private Mono<Void> deliver(Webhook webhook, WebhookEvent event) {
        var startedAt = System.currentTimeMillis();
        var attempts = new AtomicInteger();
        return Mono.defer(() -> {
                    var spec = webhook.getSpec();
                    var timestamp = String.valueOf(System.currentTimeMillis());
                    var body = payload(event);
                    outboundRequestValidator.validate(spec.getUrl());
                    var signature = sign(secret(spec), timestamp, body);
                    return send(webhook, event, spec, timestamp, body, signature, attempts, startedAt);
                })
                .doOnError(error -> record(
                        webhook,
                        event,
                        statusOf(error),
                        attempts.get(),
                        System.currentTimeMillis() - startedAt,
                        error))
                .onErrorResume(error -> Mono.empty())
                .then();
    }

    private Mono<org.springframework.http.ResponseEntity<Void>> send(
            Webhook webhook,
            WebhookEvent event,
            Webhook.Spec spec,
            String timestamp,
            String body,
            String signature,
            AtomicInteger attempts,
            long startedAt) {
        var retry = io.github.resilience4j.retry.Retry.of(
                "webhook-" + webhook.getMetadata().getName(),
                RetryConfig.custom()
                        .maxAttempts(Math.max(1, spec.getMaxAttempts()))
                        .waitDuration(RETRY_INTERVAL)
                        .retryOnException(WebhookDispatcher::retryable)
                        .build());
        return Mono.defer(() -> {
                    attempts.incrementAndGet();
                    return webClient
                            .post()
                            .uri(spec.getUrl())
                            .contentType(MediaType.APPLICATION_JSON)
                            .headers(headers -> {
                                if (signature != null) {
                                    headers.set("X-Pudans-Signature", signature);
                                }
                                headers.set("X-Pudans-Timestamp", timestamp);
                                headers.set("X-Pudans-Event", event.type());
                                headers.set("X-Pudans-Delivery", event.id());
                                outboundRequestValidator
                                        .sanitizeHeaders(spec.getHeaders())
                                        .forEach(headers::set);
                            })
                            .bodyValue(body)
                            .retrieve()
                            .toBodilessEntity()
                            .timeout(Duration.ofMillis(Math.max(1000L, spec.getTimeoutMillis())));
                })
                .transformDeferred(RetryOperator.of(retry))
                .doOnNext(response -> record(
                        webhook,
                        event,
                        response.getStatusCode().value(),
                        attempts.get(),
                        System.currentTimeMillis() - startedAt,
                        null));
    }

    private void record(
            Webhook webhook,
            WebhookEvent event,
            int statusCode,
            int attempts,
            long durationMillis,
            Throwable error) {
        var context = new LinkedHashMap<String, Object>();
        context.put("webhook", webhook.getMetadata().getName());
        context.put("eventId", event.id());
        context.put("eventType", event.type());
        context.put("url", webhook.getSpec().getUrl());
        context.put("statusCode", statusCode);
        context.put("attempts", attempts);
        context.put("durationMillis", durationMillis);
        context.put("success", error == null);
        if (error != null) {
            context.put("error", error.getClass().getName());
            context.put("message", error.getMessage());
        }
        var auditRecord = auditLog.newRecord(
                AuditIdGenerator.next(), "system", "WEBHOOK_DELIVER");
        if (event.traceFlowId() == null || event.traceFlowId().isBlank()) {
            auditRecord.setTraceFlowId(AuditIdGenerator.next());
            auditRecord.markSyntheticFlow();
        } else {
            auditRecord.setTraceFlowId(event.traceFlowId());
        }
        auditRecord.setStepIndex(1);
        auditRecord.setResourceType(Webhook.KIND);
        auditRecord.setResourceName(webhook.getMetadata().getName());
        auditRecord.setRequestMethod("POST");
        auditRecord.setRequestPath(truncate(webhook.getSpec().getUrl()));
        auditRecord.setTimestamp(Instant.now());
        auditRecord.setResult(error == null ? "SUCCESS" : "FAILURE");
        auditRecord.setDurationMs(durationMillis);
        auditRecord.setContext(context);
        auditRecord.setActionSnapshot(JsonUtils.objectToJson(context));
        auditLog.record(auditRecord);
        updateDeliveryStatus(webhook.getMetadata().getName(), error == null);
    }

    private void updateDeliveryStatus(String webhookName, boolean success) {
        var deliveredAt = Instant.now();
        Mono.defer(() -> client
                        .fetch(Webhook.class, webhookName)
                        .flatMap(webhook -> {
                            var spec = webhook.getSpec();
                            if (spec == null) {
                                return Mono.empty();
                            }
                            spec.setLastDeliveryStatus(success ? STATUS_SUCCESS : STATUS_FAILURE);
                            spec.setLastDeliveredAt(deliveredAt);
                            spec.setFailureCount(success ? 0 : spec.getFailureCount() + 1);
                            return client.update(webhook);
                        })
                        .retryWhen(Retry.backoff(3, RETRY_INTERVAL)
                                .filter(OptimisticLockingFailureException.class::isInstance)))
                .onErrorResume(error -> {
                    log.warn("无法持久化 Webhook [{}] 的投递状态", webhookName, error);
                    return Mono.empty();
                })
                .subscribe();
    }

    private String secret(Webhook.Spec spec) {
        var secret = spec.getSecret();
        if (secret == null) {
            return "";
        }
        return secretCipher.isEncrypted(secret) ? secretCipher.decrypt(secret) : secret;
    }
}
