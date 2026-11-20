package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class AiProviderBalanceProbeClient {

    public static final String CODE_NOT_CONFIGURED = "ai-provider-balance-not-configured";

    private static final String CODE_UPSTREAM = "ai-provider-balance-upstream-error";

    private static final List<String> UNIT_FIELDS =
            List.of("unit", "currency", "unit_name", "unitName", "asset");

    private static final List<String> LEFT_FIELDS = List.of(
            "left",
            "balance",
            "remaining",
            "available",
            "total_balance",
            "totalBalance",
            "credit",
            "credits",
            "amount",
            "quota");

    private final WebClient webClient;

    private final ObjectMapper mapper;

    private final AiProviderAccountService accountService;

    public AiProviderBalanceProbeClient(
            @Qualifier(AiWebClientConfiguration.BEAN_NAME) WebClient webClient,
            ObjectMapper mapper,
            AiProviderAccountService accountService) {
        this.webClient = webClient;
        this.mapper = mapper;
        this.accountService = accountService;
    }

    public static boolean configured(@Nullable AiProviderAccount account) {
        var probe = account == null || account.getSpec() == null ? null : account.getSpec().getBalanceProbe();
        return probe != null && (StringUtils.hasText(probe.getUrl()) || StringUtils.hasText(probe.getPath()));
    }

    public Mono<ProbeResult> probe(AiProvider provider, AiProviderAccount account) {
        return Mono.defer(() -> {
                    var probe = account.getSpec() == null ? null : account.getSpec().getBalanceProbe();
                    if (!configured(account) || probe == null) {
                        return Mono.error(notConfigured());
                    }
                    var uri = uri(provider, probe);
                    var token = StringUtils.hasText(probe.getToken())
                            ? Mono.just(probe.getToken().strip())
                            : accountService.apiKey(account).defaultIfEmpty("");
                    return token.flatMap(value -> request(probe, uri, value).map(this::parse));
                })
                .onErrorMap(IllegalArgumentException.class, error -> notConfigured())
                .onErrorMap(
                        error -> !(error instanceof ApiException),
                        error -> upstream("供应商余额探测失败。", error));
    }

    private Mono<String> request(
            AiProviderAccount.BalanceProbe probe, String uri, String token) {
        WebClient.RequestHeadersSpec<?> request;
        if ("POST".equalsIgnoreCase(probe.getMethod())) {
            request = webClient.post().uri(uri).bodyValue(Map.of());
        } else {
            request = webClient.get().uri(uri);
        }
        return request.headers(headers -> headers(probe, token, headers))
                .retrieve()
                .bodyToMono(String.class)
                .switchIfEmpty(Mono.error(upstream("余额响应为空。", null)));
    }

    private static void headers(
            AiProviderAccount.BalanceProbe probe, String token, HttpHeaders headers) {
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (!StringUtils.hasText(token)) {
            return;
        }
        var headerName = StringUtils.hasText(probe.getHeaderName()) ? probe.getHeaderName() : "Authorization";
        if ("Authorization".equalsIgnoreCase(headerName) && !token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            headers.setBearerAuth(token);
        } else {
            headers.set(headerName, token);
        }
    }

    private static String uri(AiProvider provider, AiProviderAccount.BalanceProbe probe) {
        var base = StringUtils.hasText(probe.getUrl())
                ? AiProviderHttpSupport.requireHttpUrl(probe.getUrl(), "余额探测 URL")
                : AiProviderHttpSupport.endpoint(provider, "");
        var path = probe.getPath() == null ? "" : probe.getPath().strip();
        if (!StringUtils.hasText(path)) {
            return base;
        }
        return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base)
                + (path.startsWith("/") ? path : "/" + path);
    }

    private ProbeResult parse(String body) {
        var root = AiProviderHttpSupport.readObject(mapper, body, "Balance");
        var data = root.get("data") != null && root.get("data").isObject() ? root.get("data") : null;
        var left = firstNumber(root, data, LEFT_FIELDS);
        if (left == null) {
            throw new AiProtocolException("余额响应缺少数值字段");
        }
        var unit = firstText(root, data, UNIT_FIELDS);
        var message = firstText(root, data, List.of("message", "msg", "description"));
        return new ProbeResult(
                unit, left, Instant.now(), StringUtils.hasText(message) ? message : "余额探测成功。");
    }

    private static @Nullable BigDecimal firstNumber(
            JsonNode root, @Nullable JsonNode data, List<String> fields) {
        for (var field : fields) {
            var value = numeric(data, field);
            if (value == null) {
                value = numeric(root, field);
            }
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable BigDecimal numeric(@Nullable JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.textValue().strip());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static @Nullable String firstText(
            JsonNode root, @Nullable JsonNode data, List<String> fields) {
        for (var field : fields) {
            var value = AiProviderHttpSupport.text(data, field);
            if (!StringUtils.hasText(value)) {
                value = AiProviderHttpSupport.text(root, field);
            }
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    public static ApiException notConfigured() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                CODE_NOT_CONFIGURED,
                "账号未配置余额探测，且供应商不支持专用余额查询。");
    }

    private static ApiException upstream(String message, @Nullable Throwable cause) {
        return new ApiException(HttpStatus.BAD_GATEWAY, CODE_UPSTREAM, message, cause);
    }

    public record ProbeResult(
            @Nullable String unit, BigDecimal left, Instant probedAt, String message) {}
}