package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.webhook.OutboundRequestValidator;
import cn.tolim.pudans.core.webhook.ValidatingAddressResolverGroup;
import cn.tolim.pudans.infra.feishu.FeishuClient;
import cn.tolim.pudans.infra.properties.OidcProperties;
import cn.tolim.pudans.infra.setting.ParameterSettingSensitiveFields;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

@Component
public class AuthenticationConnectionTester {

    private static final Duration TIMEOUT = Duration.ofSeconds(8);

    private static final String TYPE_FEISHU = "feishu";

    private static final String TYPE_OAUTH2 = "oauth2";

    private static final String TYPE_OIDC = "oidc";

    private final SecurityParameters securitySettings;

    private final FeishuClient feishuClient;

    private final WebClient webClient;

    @Autowired
    public AuthenticationConnectionTester(
            SecurityParameters securitySettings, FeishuClient feishuClient) {
        this(
                securitySettings,
                feishuClient,
                WebClient.builder()
                        // 连接期再校验解析结果，避免测试地址绕过保存期检查指向本机或内网。
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                                .resolver(new ValidatingAddressResolverGroup(
                                        new OutboundRequestValidator()))))
                        .build());
    }

    AuthenticationConnectionTester(
            SecurityParameters securitySettings,
            FeishuClient feishuClient,
            WebClient webClient) {
        this.securitySettings = securitySettings;
        this.feishuClient = feishuClient;
        this.webClient = webClient;
    }

    public Mono<ConnectionTestResult> test(ConnectionTestRequest request) {
        if (request == null) {
            return Mono.just(failure("请求体不能为空。"));
        }
        var type = normalizeType(request.type());
        if (type == null) {
            return Mono.just(failure("不支持的身份提供商类型。"));
        }
        return switch (type) {
            case TYPE_FEISHU -> testFeishu(request);
            case TYPE_OAUTH2 -> testOAuth2(request);
            case TYPE_OIDC -> testOidc(request);
            default -> Mono.just(failure("不支持的身份提供商类型。"));
        };
    }

    private Mono<ConnectionTestResult> testFeishu(ConnectionTestRequest request) {
        var settings = securitySettings.feishu();
        var appId = firstNonBlank(request.appId(), settings.appId());
        var appSecret = resolveSecret(request.appSecret(), settings.appSecret());
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            return Mono.just(failure("请填写 App ID 和 App Secret。"));
        }
        return feishuClient
                .testConnection(appId, appSecret)
                .map(token -> success("飞书连接成功。"))
                .onErrorResume(error -> Mono.just(failure("飞书连接失败：" + readable(error))));
    }

    private Mono<ConnectionTestResult> testOAuth2(ConnectionTestRequest request) {
        var provider = findOAuth2Provider(request.registrationId());
        var providerTokenUri = provider == null ? null : provider.getTokenUri();
        var tokenUri = firstNonBlank(request.tokenUri(), providerTokenUri);
        var clientId = firstNonBlank(request.clientId(), provider == null ? null : provider.getClientId());
        var clientSecret = resolveSecret(
                request.clientSecret(), provider == null ? null : provider.getClientSecret());
        var scopes = firstNonBlank(request.scopes(), provider == null ? null : provider.getScopes());
        if (!isHttpUrl(tokenUri)) {
            return Mono.just(failure("请填写有效的令牌地址。"));
        }
        // 已保存的密钥只允许发往配置过的地址，避免改地址后把密钥带出去。
        if (usesStoredSecret(request.clientSecret())
                && providerTokenUri != null
                && !sameUri(tokenUri, providerTokenUri)) {
            return Mono.just(failure("令牌地址与已保存的提供方不一致，不能使用已保存的 Client Secret。"));
        }
        // 令牌地址可由请求体覆盖，先拦掉本机与内网目标。
        var unsafeEndpoint = rejectUnsafeEndpoint(tokenUri);
        if (unsafeEndpoint != null) {
            return Mono.just(unsafeEndpoint);
        }
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return Mono.just(failure("请填写 Client ID 和 Client Secret。"));
        }
        return testTokenEndpoint(tokenUri, clientId, clientSecret, scopes);
    }

    private Mono<ConnectionTestResult> testOidc(ConnectionTestRequest request) {
        var provider = findOidcProvider(request.registrationId());
        var issuerUri = firstNonBlank(request.issuerUri(), provider == null ? null : provider.getIssuerUri());
        if (!StringUtils.hasText(issuerUri)) {
            return testOAuth2(request);
        }
        if (!isHttpUrl(issuerUri)) {
            return Mono.just(failure("请填写有效的 Issuer URI。"));
        }
        // 发现请求同样会外联，issuer 也要过一遍地址校验。
        var unsafeEndpoint = rejectUnsafeEndpoint(issuerUri);
        if (unsafeEndpoint != null) {
            return Mono.just(unsafeEndpoint);
        }
        var discoveryUri = issuerUri.replaceAll("/+$", "") + "/.well-known/openid-configuration";
        return webClient
                .get()
                .uri(discoveryUri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .timeout(TIMEOUT)
                .map(document -> {
                    if (document.get("authorization_endpoint") == null
                            || document.get("token_endpoint") == null) {
                        return failure("OIDC 发现文档缺少 Authorization 或 Token 端点。");
                    }
                    return success("OIDC 发现文档读取成功。");
                })
                .onErrorResume(
                        WebClientResponseException.class,
                        error -> Mono.just(failure(
                                "OIDC 发现请求失败：HTTP " + error.getStatusCode().value() + "。")))
                .onErrorResume(error -> Mono.just(failure("OIDC 发现请求失败：" + readable(error))));
    }

    private Mono<ConnectionTestResult> testTokenEndpoint(
            String tokenUri, String clientId, String clientSecret, String scopes) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        if (StringUtils.hasText(scopes)) {
            form.add("scope", scopes);
        }
        return webClient
                .post()
                .uri(tokenUri)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .exchangeToMono(response -> response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> resultForTokenResponse(response.statusCode().value(), body)))
                .timeout(TIMEOUT)
                .onErrorResume(
                        WebClientResponseException.class,
                        error -> Mono.just(failure(
                                "令牌端点请求失败：HTTP " + error.getStatusCode().value() + "。")))
                .onErrorResume(error -> Mono.just(failure("令牌端点请求失败：" + readable(error))));
    }

    private static ConnectionTestResult resultForTokenResponse(int status, String body) {
        var lowerBody = body.toLowerCase(Locale.ROOT);
        if (status >= 200 && status < 300) {
            return success("令牌端点连接成功。");
        }
        if (status == 401 || lowerBody.contains("invalid_client")) {
            return failure("Client ID 或 Client Secret 无效。");
        }
        if (lowerBody.contains("unsupported_grant_type") || lowerBody.contains("invalid_grant")) {
            return success("令牌端点可达，但该提供方不支持 client_credentials 测试。");
        }
        if (lowerBody.contains("invalid_scope")) {
            return success("令牌端点可达，但 scope 未被接受，请检查权限范围。");
        }
        return failure("令牌端点返回 HTTP " + status + "。");
    }

    private ParameterSetting.OAuth2Provider findOAuth2Provider(String registrationId) {
        if (!StringUtils.hasText(registrationId)) {
            return null;
        }
        return securitySettings.oauth2Providers().stream()
                .filter(Objects::nonNull)
                .filter(provider -> registrationId.equals(provider.getRegistrationId()))
                .findFirst()
                .orElse(null);
    }

    private OidcProperties.Provider findOidcProvider(String registrationId) {
        if (!StringUtils.hasText(registrationId)) {
            return null;
        }
        return securitySettings.oidcProviders().stream()
                .filter(Objects::nonNull)
                .filter(provider -> registrationId.equals(provider.getRegistrationId()))
                .findFirst()
                .orElse(null);
    }

    private static String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        var normalized = type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case TYPE_FEISHU, TYPE_OAUTH2, TYPE_OIDC -> normalized;
            default -> null;
        };
    }

    private static String resolveSecret(String supplied, String stored) {
        if (StringUtils.hasText(supplied)
                && !ParameterSettingSensitiveFields.MASK.equals(supplied)) {
            return supplied;
        }
        return stored;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static boolean usesStoredSecret(String supplied) {
        return !StringUtils.hasText(supplied)
                || ParameterSettingSensitiveFields.MASK.equals(supplied);
    }

    private static boolean sameUri(String left, String right) {
        return left.replaceAll("/+$", "").equals(right.replaceAll("/+$", ""));
    }

    // 拒绝本机、内网与云元数据地址，避免连接测试被当作 SSRF 中转。
    private static ConnectionTestResult rejectUnsafeEndpoint(String url) {
        var host = OutboundRequestValidator.stripBrackets(URI.create(url).getHost());
        if (OutboundRequestValidator.isForbiddenHost(host)
                || (OutboundRequestValidator.isIpLiteral(host)
                        && OutboundRequestValidator.isBlockedLiteral(host))) {
            return failure("不允许连接本机、内网或云元数据地址。");
        }
        return null;
    }

    private static boolean isHttpUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            var uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String readable(Throwable error) {
        var message = error.getMessage();
        return StringUtils.hasText(message) ? message : error.getClass().getSimpleName();
    }

    private static ConnectionTestResult success(String message) {
        return new ConnectionTestResult(true, message);
    }

    private static ConnectionTestResult failure(String message) {
        return new ConnectionTestResult(false, message);
    }

    public record ConnectionTestRequest(
            String type,
            String registrationId,
            String issuerUri,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String clientId,
            String clientSecret,
            String appId,
            String appSecret,
            String scopes) {}

    public record ConnectionTestResult(boolean success, String message) {}
}
