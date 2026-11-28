package cn.tolim.pudans.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class IamSessionValidationClient implements GlobalSessionValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(IamSessionValidationClient.class);
    private static final String RESPONSE_DATA_FIELD = "data";
    private static final String RESPONSE_ACTIVE_FIELD = "active";
    private static final String RESPONSE_SUBJECT_FIELD = "sub";
    private static final String RESPONSE_REASON_FIELD = "reason";
    private static final String REQUEST_SID_FIELD = "sid";
    private static final String REQUEST_APP_CODE_FIELD = "appCode";
    private static final String INACTIVE_REASON = "inactive";
    private static final String UNAVAILABLE_REASON = "session-validation-unavailable";

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String validateUrl;
    private final long cacheTtlMillis;
    private final Duration timeout;
    private final Map<String, CachedValidation> cache = new ConcurrentHashMap<>();

    public IamSessionValidationClient(WebClient.Builder webClientBuilder,
                                      ObjectMapper objectMapper,
                                      String validateUrl,
                                      long cacheTtlSeconds,
                                      long timeoutMillis) {
        if (validateUrl == null || validateUrl.isBlank()) {
            throw new IllegalArgumentException("Session validation URL must not be blank");
        }
        if (cacheTtlSeconds < 0) {
            throw new IllegalArgumentException("Session validation cache TTL must not be negative");
        }
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Session validation timeout must be positive");
        }
        this.webClient = webClientBuilder.build();
        this.objectMapper = objectMapper;
        this.validateUrl = validateUrl;
        this.cacheTtlMillis = cacheTtlSeconds * 1000L;
        this.timeout = Duration.ofMillis(timeoutMillis);
    }

    @Override
    public Mono<SessionValidationResult> validate(String sid, String appCode) {
        if (sid == null || sid.isBlank()) {
            return Mono.just(SessionValidationResult.inactive("missing-sid"));
        }
        String cacheKey = appCode == null || appCode.isBlank() ? sid : sid + "|" + appCode;
        SessionValidationResult cached = readCache(cacheKey);
        if (cached != null) {
            return Mono.just(cached);
        }
        return requestValidation(sid, appCode)
                .map(result -> {
                    writeCache(cacheKey, result);
                    return result;
                })
                .onErrorResume(exception -> {
                    LOGGER.warn("Global session validation failed: {}", exception.getMessage());
                    return Mono.just(SessionValidationResult.inactive(UNAVAILABLE_REASON));
                });
    }

    private Mono<SessionValidationResult> requestValidation(String sid, String appCode) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put(REQUEST_SID_FIELD, sid);
        if (appCode != null && !appCode.isBlank()) {
            body.put(REQUEST_APP_CODE_FIELD, appCode);
        }
        return webClient.post()
                .uri(validateUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(objectMapper.writeValueAsString(body))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> Mono.error(
                        new SessionValidationException("IAM responded with status " + response.statusCode().value())))
                .bodyToMono(String.class)
                .timeout(timeout)
                .map(this::parseResult);
    }

    private SessionValidationResult parseResult(String body) {
        if (body == null || body.isBlank()) {
            return SessionValidationResult.inactive("empty-session-validation-response");
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode payload = root.path(RESPONSE_DATA_FIELD);
        if (payload.isObject()) {
            root = payload;
        }
        if (!root.path(RESPONSE_ACTIVE_FIELD).asBoolean(false)) {
            String reason = root.path(RESPONSE_REASON_FIELD).asString("");
            return SessionValidationResult.inactive(reason.isBlank() ? INACTIVE_REASON : reason);
        }
        return SessionValidationResult.active(root.path(RESPONSE_SUBJECT_FIELD).asString(""));
    }

    private SessionValidationResult readCache(String cacheKey) {
        CachedValidation cached = cache.get(cacheKey);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtMillis() <= System.currentTimeMillis()) {
            cache.remove(cacheKey, cached);
            return null;
        }
        return cached.result();
    }

    private void writeCache(String cacheKey, SessionValidationResult result) {
        if (cacheTtlMillis <= 0) {
            return;
        }
        cache.put(cacheKey, new CachedValidation(result, System.currentTimeMillis() + cacheTtlMillis));
    }

    private record CachedValidation(SessionValidationResult result, long expiresAtMillis) {
    }

    private static final class SessionValidationException extends RuntimeException {

        private SessionValidationException(String message) {
            super(message);
        }
    }
}