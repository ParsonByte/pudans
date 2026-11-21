package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

abstract class AbstractMinimaxClient {

    protected final ObjectMapper mapper;

    protected final ProviderAccounts accountService;

    private final WebClient webClient;

    private final String errorCode;

    private final String defaultError;

    protected AbstractMinimaxClient(
            WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts accountService,
            String errorCode,
            String defaultError) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.accountService = Objects.requireNonNull(accountService, "供应商账号服务不能为空");
        this.errorCode = Objects.requireNonNull(errorCode, "错误码不能为空");
        this.defaultError = Objects.requireNonNull(defaultError, "错误说明不能为空");
    }

    protected final WebClient webClient() {
        return webClient;
    }

    protected final <T> Mono<T> withApiKey(
            AiProviderAccount account, Function<String, Mono<T>> call) {
        return accountService.apiKey(account).defaultIfEmpty("").flatMap(call);
    }

    protected final HttpHeaders headers(AiProvider provider, String apiKey, MediaType accept) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setAccept(List.of(accept));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
            headers.set("x-api-key", apiKey);
        }
        return headers;
    }

    protected final JsonNode validate(JsonNode root) {
        var baseResp = root == null ? null : root.get("base_resp");
        if (baseResp == null || !baseResp.isObject()) {
            return root;
        }
        var statusCode = AiProviderHttpSupport.integer(baseResp, "status_code");
        if (statusCode == null || statusCode == 0) {
            return root;
        }
        var message = AiProviderHttpSupport.text(baseResp, "status_msg");
        throw new ApiException(
                statusOf(statusCode),
                errorCode,
                "MiniMax 上游错误 " + statusCode + ": " + (message == null ? defaultError : message));
    }

    private static HttpStatus statusOf(int statusCode) {
        return switch (statusCode) {
            case 1004 -> HttpStatus.UNAUTHORIZED;
            case 1008 -> HttpStatus.PAYMENT_REQUIRED;
            case 1002, 1039 -> HttpStatus.TOO_MANY_REQUESTS;
            case 1026, 1027, 2013 -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    protected final ApiException upstreamError(Throwable error) {
        if (error instanceof ApiException apiException) {
            return apiException;
        }
        if (error instanceof WebClientResponseException response) {
            var reason = errorMessage(response.getResponseBodyAsString());
            var status = response.getStatusCode().is4xxClientError()
                    ? HttpStatus.valueOf(response.getStatusCode().value())
                    : HttpStatus.BAD_GATEWAY;
            return new ApiException(status, errorCode, reason, error);
        }
        return upstreamError(defaultError, error);
    }

    protected final ApiException upstreamError(String reason, Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, errorCode, reason, cause);
    }

    protected final ApiException upstreamError(int statusCode, String body) {
        var status = statusCode >= 400 && statusCode < 500
                ? HttpStatus.valueOf(statusCode)
                : HttpStatus.BAD_GATEWAY;
        return new ApiException(status, errorCode, errorMessage(body), null);
    }

    protected final String errorMessage(String body) {
        if (!StringUtils.hasText(body)) {
            return defaultError;
        }
        try {
            var root = mapper.readTree(body);
            var message = AiProviderHttpSupport.text(root.get("base_resp"), "status_msg");
            if (!StringUtils.hasText(message)) {
                message = AiProviderHttpSupport.text(root.get("error"), "message");
            }
            return StringUtils.hasText(message) ? message : defaultError;
        } catch (JsonProcessingException error) {
            return defaultError;
        }
    }
}
