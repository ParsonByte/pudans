package cn.tolim.pudans.core.storage.driver.onedrive;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

public class OneDriveTokenClient {

    private static final String TOKEN_URL = "https://login.microsoftonline.com/%s/oauth2/v2.0/token";

    private final String clientId;

    private final String clientSecret;

    private final String refreshToken;

    private final String tenantId;

    private final WebClient webClient;

    private volatile Token token;

    public OneDriveTokenClient(
            StoragePolicy policy, String clientSecret, String refreshToken, WebClient webClient) {
        var spec = policy.getSpec();
        var settings = spec.getSettings();
        this.clientId = spec.getBucketName();
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.tenantId = settings != null && StringUtils.hasText(settings.getTenantId())
                ? settings.getTenantId()
                : "common";
        this.webClient = webClient;
    }

    public Mono<String> accessToken() {
        var current = token;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
            return Mono.just(current.value());
        }
        return webClient
                .post()
                .uri(TOKEN_URL.formatted(tenantId))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("client_id", clientId)
                        .with("client_secret", clientSecret)
                        .with("refresh_token", refreshToken)
                        .with("grant_type", "refresh_token")
                        .with("scope", "https://graph.microsoft.com/.default"))
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    var json = JsonMapper.shared().readTree(body);
                    var value = json.path("access_token").asString();
                    var expiresIn = json.path("expires_in").asLong(3600);
                    if (!StringUtils.hasText(value)) {
                        throw new ApiException(
                                HttpStatus.UNAUTHORIZED,
                                "storage-onedrive-auth-failed",
                                "OneDrive 授权失败。");
                    }
                    return new Token(value, Instant.now().plusSeconds(Math.max(60, expiresIn)));
                })
                .doOnNext(refreshed -> token = refreshed)
                .map(Token::value)
                .onErrorMap(
                        WebClientResponseException.class,
                        error -> new ApiException(
                                HttpStatus.UNAUTHORIZED,
                                "storage-onedrive-auth-failed",
                                "OneDrive 授权失败。",
                                error));
    }

    private record Token(String value, Instant expiresAt) {}
}
