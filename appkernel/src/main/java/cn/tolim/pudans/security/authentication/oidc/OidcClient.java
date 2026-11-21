package cn.tolim.pudans.security.authentication.oidc;

import cn.tolim.pudans.infra.properties.OidcProperties;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class OidcClient {

    private final SecurityParameters securitySettings;

    private final WebClient webClient = WebClient.builder().build();

    // 将声明值安全转换为字符串，空值保持为空。
    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    // 按注册标识查找配置，未知标识直接报错。
    public OidcProperties.Provider provider(String registrationId) {
        return securitySettings.oidcProviders().stream()
                .filter(provider -> registrationId.equals(provider.getRegistrationId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 OIDC 提供方：" + registrationId));
    }

    // 拼接携带状态参数的授权码跳转地址。
    public String authorizationUrl(String registrationId, String redirectUri, String state) {
        var provider = provider(registrationId);
        return UriComponentsBuilder.fromUriString(provider.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("scope", String.join(" ", provider.getScopes()))
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    // 用授权码换取令牌，再拉取用户信息声明。
    public Mono<OidcUserInfo> exchange(String registrationId, String code, String redirectUri) {
        var provider = provider(registrationId);
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("client_id", provider.getClientId());
        if (StringUtils.hasText(provider.getClientSecret())) {
            form.add("client_secret", provider.getClientSecret());
        }
        return webClient
                .post()
                .uri(provider.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(form))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .flatMap(tokenResponse -> {
                    var accessToken = tokenResponse.get("access_token");
                    if (accessToken == null) {
                        return Mono.error(
                                new OidcException("OIDC 令牌响应中没有 access_token：" + tokenResponse));
                    }
                    var userInfoUri = provider.getUserInfoUri();
                    if (!StringUtils.hasText(userInfoUri)) {
                        return Mono.error(new OidcException("OIDC 提供方没有 userinfo 端点"));
                    }
                    return webClient
                            .get()
                            .uri(userInfoUri)
                            .headers(headers -> headers.setBearerAuth(String.valueOf(accessToken)))
                            .retrieve()
                            .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
                })
                .map(userInfo -> {
                    var subject = userInfo.get(provider.getUserNameAttribute());
                    if (subject == null) {
                        throw new OidcException(
                                "OIDC userinfo 响应中缺少声明 [" + provider.getUserNameAttribute() + "]：" + userInfo);
                    }
                    return new OidcUserInfo(
                            String.valueOf(subject),
                            asString(userInfo.get("name")),
                            asString(userInfo.get("email")),
                            asString(userInfo.get("picture")),
                            new LinkedHashMap<>(userInfo));
                });
    }

    public record OidcUserInfo(
            String subject, String name, String email, String picture, Map<String, Object> attributes) {}


    public static class OidcException extends RuntimeException {
        // 表示协议响应缺少登录所需字段。
        public OidcException(String message) {
            super(message);
        }
    }
}
