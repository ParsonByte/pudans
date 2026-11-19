package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import cn.tolim.pudans.infra.exception.ApiException;
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

abstract class AbstractQianwenClient {

    protected final ObjectMapper mapper;

    protected final AiProviderAccountService accountService;

    private final WebClient webClient;

    private final String errorCode;

    private final String defaultError;

    protected AbstractQianwenClient(
            WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService,
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

    protected final HttpHeaders headers(AiProvider provider, String apiKey) {
        var headers = new HttpHeaders();
        AiProviderHttpSupport.addProviderHeaders(headers, provider);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(apiKey)) {
            headers.setBearerAuth(apiKey);
        }
        return headers;
    }

    protected final JsonNode read(String body) {
        try {
            var node = mapper.readTree(body);
            if (node == null) {
                throw error("千问返回空响应。");
            }
            return node;
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw error("无法解析千问响应。", error);
        }
    }

    protected final ApiException upstream(Throwable error) {
        if (error instanceof ApiException apiException) {
            return apiException;
        }
        if (error instanceof WebClientResponseException response) {
            var status = response.getStatusCode().is4xxClientError()
                    ? HttpStatus.valueOf(response.getStatusCode().value())
                    : HttpStatus.BAD_GATEWAY;
            return new ApiException(status, errorCode, defaultError, error);
        }
        return new ApiException(HttpStatus.BAD_GATEWAY, errorCode, defaultError, error);
    }

    protected final ApiException error(String reason) {
        return new ApiException(HttpStatus.BAD_GATEWAY, errorCode, reason);
    }

    protected final ApiException error(String reason, Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, errorCode, reason, cause);
    }

    protected static MediaType mediaType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (org.springframework.http.InvalidMediaTypeException error) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
