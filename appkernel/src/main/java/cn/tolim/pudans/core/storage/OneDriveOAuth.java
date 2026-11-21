package cn.tolim.pudans.core.storage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.infra.contract.ExternalUrlSupplier;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageIo;
import cn.tolim.pudans.core.storage.driver.onedrive.OneDriveTokenClient;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Service
public class OneDriveOAuth {

    private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/authorize";

    private static final String TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    private static final String GRAPH = "https://graph.microsoft.com/v1.0";

    private static final String SCOPE = "offline_access Files.ReadWrite.All";

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private static final String STATE_SESSION_ATTRIBUTE = "pudans.storage.onedrive.state";

    private final Policies policies;

    private final TransferTokens transferTokens;

    private final ExternalUrlSupplier externalUrl;

    private final StorageDriverResolver driverResolver;

    private final StoragePolicySecretCipher secretCipher;

    private final WebClient webClient;

    // 使用默认客户端配置创建生产环境实例。
    @Autowired
    public OneDriveOAuth(
            Policies policies,
            TransferTokens transferTokens,
            ExternalUrlSupplier externalUrl,
            StorageDriverResolver driverResolver,
            StoragePolicySecretCipher secretCipher) {
        this(
                policies,
                transferTokens,
                externalUrl,
                driverResolver,
                secretCipher,
                WebClient.builder().build());
    }

    // 注入客户端便于测试替换网络调用。
    OneDriveOAuth(
            Policies policies,
            TransferTokens transferTokens,
            ExternalUrlSupplier externalUrl,
            StorageDriverResolver driverResolver,
            StoragePolicySecretCipher secretCipher,
            WebClient webClient) {
        this.policies = policies;
        this.transferTokens = transferTokens;
        this.externalUrl = externalUrl;
        this.driverResolver = driverResolver;
        this.secretCipher = secretCipher;
        this.webClient = webClient;
    }

    // 授权状态同时写入会话，回调时必须来自同一个浏览器会话。
    public Mono<OAuthUrl> authorizeUrl(String policyName, ServerWebExchange exchange) {
        return policies.get(policyName).flatMap(policy -> {
            ensureOneDrive(policy);
            var redirectUri = callbackUri();
            var state = transferTokens.signOAuthState(policyName, STATE_TTL);
            var url = AUTHORIZE_URL.formatted(tenant(policy))
                    + "?client_id=" + encode(clientId(policy))
                    + "&response_type=code"
                    + "&redirect_uri=" + encode(redirectUri)
                    + "&response_mode=query"
                    + "&scope=" + encode(SCOPE)
                    + "&state=" + encode(state);
            return exchange.getSession()
                    .doOnNext(session -> session.getAttributes().put(STATE_SESSION_ATTRIBUTE, state))
                    .thenReturn(new OAuthUrl(url, state, redirectUri));
        });
    }

    // 校验签名后消费会话中的状态，防止授权回调被重放或跨会话复用。
    public Mono<OAuthStatus> callback(String code, String state, ServerWebExchange exchange) {
        var policyName = transferTokens.verifyOAuthState(state);
        return exchange.getSession().flatMap(session -> {
            var expectedState = (String) session.getAttributes().remove(STATE_SESSION_ATTRIBUTE);
            if (!Objects.equals(expectedState, state)) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "storage-onedrive-state-invalid",
                        "OneDrive 授权状态无效或已过期。"));
            }
            return completeCallback(policyName, code);
        });
    }

    // 用授权码换取刷新令牌并加密保存。
    private Mono<OAuthStatus> completeCallback(String policyName, String code) {
        return policies.get(policyName).flatMap(policy -> {
            ensureOneDrive(policy);
            var credentials = policy.getSpec().getCredentials();
            if (credentials == null || !StringUtils.hasText(credentials.getClientSecret())) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "storage-onedrive-client-secret-missing",
                        "OneDrive 客户端密钥未配置。"));
            }
            var clientSecret = secretCipher.decrypt(credentials.getClientSecret());
            return webClient
                    .post()
                    .uri(TOKEN_URL.formatted(tenant(policy)))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData("client_id", clientId(policy))
                            .with("client_secret", clientSecret)
                            .with("code", code)
                            .with("redirect_uri", callbackUri())
                            .with("grant_type", "authorization_code")
                            .with("scope", SCOPE))
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(this::refreshToken)
                    .flatMap(refreshToken -> {
                        credentials.setToken(secretCipher.encrypt(refreshToken));
                        return policies.update(policy);
                    })
                    .doOnNext(updated -> driverResolver.evict(policyName))
                    .map(updated -> new OAuthStatus(policyName, true, "OneDrive 授权成功。"))
                    .onErrorMap(
                            WebClientResponseException.class,
                            error -> new ApiException(
                                    HttpStatus.BAD_GATEWAY,
                                    "storage-onedrive-oauth-failed",
                                    "OneDrive 授权失败。",
                                    error));
        });
    }

    // 返回策略是否已配置有效授权令牌。
    public Mono<OAuthStatus> status(String policyName) {
        return policies.get(policyName).map(policy -> {
            ensureOneDrive(policy);
            var credentials = policy.getSpec().getCredentials();
            var configured = credentials != null && StringUtils.hasText(credentials.getToken());
            return new OAuthStatus(
                    policyName,
                    configured,
                    configured ? "已配置 OneDrive 授权令牌。" : "尚未完成 OneDrive 授权。");
        });
    }

    // 将站点地址解析为 OneDrive 驱动器标识。
    public Mono<String> resolveRoot(String policyName, String siteUrl) {
        return policies.get(policyName).flatMap(policy -> {
            ensureOneDrive(policy);
            var credentials = policy.getSpec().getCredentials();
            if (credentials == null
                    || !StringUtils.hasText(credentials.getClientSecret())
                    || !StringUtils.hasText(credentials.getToken())) {
                return Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "storage-onedrive-credentials-missing",
                        "OneDrive 授权凭据不完整。"));
            }
            var tokenClient = new OneDriveTokenClient(
                    policy,
                    secretCipher.decrypt(credentials.getClientSecret()),
                    secretCipher.decrypt(credentials.getToken()),
                    webClient);
            return tokenClient.accessToken().flatMap(accessToken -> {
                var graphUrl = siteGraphUrl(siteUrl);
                return graphGet(graphUrl, accessToken)
                        .map(OneDriveOAuth::readId)
                        .filter(StringUtils::hasText)
                        .flatMap(siteId -> graphGet(
                                        GRAPH + "/sites/" + StorageIo.encodePath(siteId) + "/drive?$select=id",
                                        accessToken)
                                .map(OneDriveOAuth::readId)
                                .filter(StringUtils::hasText))
                        .switchIfEmpty(Mono.error(new ApiException(
                                HttpStatus.BAD_GATEWAY,
                                "storage-onedrive-root-missing",
                                "OneDrive 站点未返回驱动器 ID。")));
            });
        });
    }

    // 统一处理 Graph 请求的鉴权与状态码。
    private Mono<String> graphGet(String url, String accessToken) {
        return webClient
                .get()
                .uri(url)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .onStatus(status -> status.value() == 404, response -> Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "storage-onedrive-site-not-found", "OneDrive 站点不存在。")))
                .onStatus(status -> status.value() == 401 || status.value() == 403, response -> Mono.error(
                        new ApiException(HttpStatus.FORBIDDEN, "storage-access-denied", "存储访问被拒绝。")))
                .onStatus(status -> status.isError(), response -> Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, "storage-driver-error", "OneDrive 站点查询失败。")))
                .bodyToMono(String.class);
    }

    // 提取 Graph 响应中的资源标识。
    private static String readId(String body) {
        return JsonMapper.shared().readTree(body).path("id").asString();
    }

    // 将站点地址转换为 Graph 站点资源路径。
    private static String siteGraphUrl(String siteUrl) {
        if (!StringUtils.hasText(siteUrl)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "storage-onedrive-site-required", "SharePoint 站点地址不能为空。");
        }
        var value = siteUrl.startsWith("http://") || siteUrl.startsWith("https://")
                ? siteUrl
                : "https://" + siteUrl;
        var uri = URI.create(value);
        var host = uri.getHost();
        if (!StringUtils.hasText(host)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "storage-onedrive-site-invalid", "SharePoint 站点地址无效。");
        }
        var path = uri.getPath() == null ? "" : uri.getPath();
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return GRAPH + "/sites/" + host + ":/" + StorageIo.encodePath(path);
    }

    // 读取刷新令牌，缺失时按授权失败处理。
    private String refreshToken(String body) {
        var json = JsonMapper.shared().readTree(body);
        var refreshToken = json.path("refresh_token").asString();
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "storage-onedrive-oauth-failed",
                    "OneDrive 授权响应缺少刷新令牌。");
        }
        return refreshToken;
    }

    // 拼接对外可访问的 OAuth 回调地址。
    private String callbackUri() {
        var base = externalUrl.get().toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + ApiGroups.API_PREFIX + "/storagepolicies/-/oauth/callback";
    }

    // 按 URL 查询参数规则编码，空格使用 %20。
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    // 未配置租户时回退到通用租户端点。
    private static String tenant(StoragePolicy policy) {
        var settings = policy.getSpec().getSettings();
        return settings != null && StringUtils.hasText(settings.getTenantId())
                ? settings.getTenantId()
                : "common";
    }

    // 从存储桶字段读取 OneDrive 客户端标识。
    private static String clientId(StoragePolicy policy) {
        var clientId = policy.getSpec().getBucketName();
        if (!StringUtils.hasText(clientId)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "storage-onedrive-client-id-missing",
                    "OneDrive 客户端 ID 未配置。");
        }
        return clientId;
    }

    // 仅允许 OneDrive 类型策略执行 OAuth 授权。
    private static void ensureOneDrive(StoragePolicy policy) {
        if (policy.getSpec().getType() != StoragePolicyType.ONEDRIVE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "storage-oauth-unsupported",
                    "该存储策略不支持 OneDrive OAuth 授权。");
        }
    }

    public record OAuthUrl(String url, String state, String redirectUri) {}

    public record OAuthStatus(String policy, boolean authorized, String message) {}
}
