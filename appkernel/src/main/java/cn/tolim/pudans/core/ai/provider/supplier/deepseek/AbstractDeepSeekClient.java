package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
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

abstract class AbstractDeepSeekClient {

    protected final ObjectMapper mapper;

    protected final ProviderAccounts providerAccounts;

    private final WebClient webClient;

    private final String errorCode;

    private final String defaultError;

    protected AbstractDeepSeekClient(
            WebClient webClient,
            ObjectMapper mapper,
            ProviderAccounts providerAccounts,
            String errorCode,
            String defaultError) {
        this.webClient = Objects.requireNonNull(webClient, "AI WebClient 不能为空");
        this.mapper = Objects.requireNonNull(mapper, "ObjectMapper 不能为空");
        this.providerAccounts = Objects.requireNonNull(providerAccounts, "供应商账号服务不能为空");
        this.errorCode = Objects.requireNonNull(errorCode, "错误码不能为空");
        this.defaultError = Objects.requireNonNull(defaultError, "错误说明不能为空");
    }

    protected final WebClient webClient() {
        return webClient;
    }

    protected final <T> Mono<T> withApiKey(
            AiProviderAccount account, Function<String, Mono<T>> call) {
        return providerAccounts.apiKey(account).defaultIfEmpty("").flatMap(call);
    }

    protected final HttpHeaders headers(AiProvider provider, String apiKey, MediaType accept) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setAccept(List.of(accept));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
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
            var message = AiProviderHttpSupport.text(root.get("error"), "message");
            return StringUtils.hasText(message) ? message : defaultError;
        } catch (JsonProcessingException error) {
            return defaultError;
        }
    }
}
