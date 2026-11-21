package cn.tolim.pudans.security.authentication.login;

import cn.tolim.pudans.security.contract.AuthProvider;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.rbac.permission.UiPermissions;
import cn.tolim.pudans.extension.contract.endpoint.CustomEndpoint;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.extension.contract.GroupVersion;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.AuthProviders;
import cn.tolim.pudans.security.captcha.Captchas;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

// 公开登录契约：当前会话、已启用的 OAuth2 提供方以及首次运行状态。
@Component
@RequiredArgsConstructor
public class LoginPublicEndpoint implements CustomEndpoint {

    private static final String FEISHU_PROVIDER = "feishu";

    private static final String FEISHU_AUTHORIZATION_PATH =
            ApiGroups.API_PREFIX + "/oauth2/authorization/feishu";

    private final SecurityProperties securityProperties;

    private final Accounts accounts;

    private final AuthProviders authProviders;

    private final SecurityParameters securitySettings;

    private final Captchas captchas;

    private final InitializationStateGetter initializationStateGetter;

    private final UiPermissions uiPermissionService;

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "LoginV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/login/session", this::session, builder -> builder.operationId("getLoginSession")
                        .description("Returns the current browser session for the auth application.")
                        .tag(tag)
                        .response(org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder()
                                .implementation(SessionView.class)))
                .GET("/login/providers", this::providers, builder -> builder.operationId("listLoginProviders")
                        .description("Lists the enabled non-form auth providers shown on the sign-in page.")
                        .tag(tag)
                        .response(org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder()
                                .implementationArray(LoginProviderView.class)))
                .GET("/login/status", this::status, builder -> builder.operationId("getLoginStatus")
                        .description("Returns the first-run state and post-login destination.")
                        .tag(tag)
                        .response(org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder()
                                .implementation(LoginStatusView.class)))
                .build();
    }

    private Mono<ServerResponse> session(ServerRequest request) {
        return currentAuthentication(request)
                .filter(this::isSignedIn)
                .flatMap(this::signedInSession)
                .switchIfEmpty(Mono.just(SessionView.anonymous(securityProperties.getPostLoginUrl())))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<SessionView> signedInSession(Authentication authentication) {
        var username = authentication.getName();
        var user = accounts
                .getUser(username)
                .map(found -> new SessionView(
                        true,
                        username,
                        found.getSpec().getDisplayName(),
                        found.getSpec().getAvatar(),
                        securityProperties.getPostLoginUrl(),
                        false,
                        List.of()))
                .defaultIfEmpty(new SessionView(
                        true,
                        username,
                        username,
                        null,
                        securityProperties.getPostLoginUrl(),
                        false,
                        List.of()));
        return Mono.zip(
                        user,
                        uiPermissionService.uiPermissionsOf(username).defaultIfEmpty(List.of()),
                        uiPermissionService.isConsoleAccessDisallowed(username).defaultIfEmpty(false))
                .map(tuple -> new SessionView(
                        true,
                        tuple.getT1().username(),
                        tuple.getT1().displayName(),
                        tuple.getT1().avatar(),
                        securityProperties.getPostLoginUrl(),
                        tuple.getT3(),
                        tuple.getT2()));
    }

    private Mono<ServerResponse> providers(ServerRequest request) {
        return authProviders
                .getEnabledProviders()
                .filter(provider -> !AuthProvider.AuthType.FORM.equals(provider.getSpec().getAuthType()))
                .filter(provider -> StringUtils.hasText(provider.getSpec().getAuthenticationUrl()))
                .map(provider -> new LoginProviderView(
                        provider.getMetadata().getName(),
                        provider.getSpec().getDisplayName(),
                        provider.getSpec().getLogo(),
                        provider.getSpec().getAuthenticationUrl(),
                        provider.getSpec().getHelpPage()))
                .collectList()
                .map(this::includeFeishu)
                .map(this::includeConfiguredProviders)
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    private List<LoginProviderView> includeConfiguredProviders(List<LoginProviderView> views) {
        var providers = new ArrayList<>(views);
        var names = new HashSet<>(providers.stream().map(LoginProviderView::name).toList());
        securitySettings.oauth2Providers().forEach(provider -> {
            var name = provider.getRegistrationId();
            if (FEISHU_PROVIDER.equals(name) || !names.add(name)) {
                return;
            }
            providers.add(new LoginProviderView(
                    name,
                    displayName(provider.getDisplayName(), name),
                    provider.getLogo(),
                    "/oauth2/authorization/" + name,
                    null));
        });
        securitySettings.oidcProviders().forEach(provider -> {
            var name = provider.getRegistrationId();
            if (FEISHU_PROVIDER.equals(name) || !names.add(name)) {
                return;
            }
            providers.add(new LoginProviderView(
                    name,
                    displayName(provider.getDisplayName(), name),
                    null,
                    "/oauth2/authorization/" + name,
                    null));
        });
        return providers.stream()
                .sorted(Comparator.comparing(LoginProviderView::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(LoginProviderView::name))
                .toList();
    }

    private static String displayName(String displayName, String fallback) {
        return StringUtils.hasText(displayName) ? displayName : fallback;
    }

    private List<LoginProviderView> includeFeishu(List<LoginProviderView> views) {
        var providers = new ArrayList<>(views);
        var feishuConfigured = securitySettings.feishu().isConfigured();
        var feishuIndex = -1;

        for (var index = 0; index < providers.size(); index++) {
            if (FEISHU_PROVIDER.equals(providers.get(index).name())) {
                feishuIndex = index;
                break;
            }
        }

        var existing = feishuIndex >= 0 ? providers.get(feishuIndex) : null;
        var feishu = new LoginProviderView(
                FEISHU_PROVIDER,
                "飞书",
                existing == null ? null : existing.logo(),
                feishuConfigured ? FEISHU_AUTHORIZATION_PATH : null,
                existing == null ? null : existing.helpPage());

        if (feishuIndex >= 0) {
            providers.set(feishuIndex, feishu);
        } else {
            providers.add(feishu);
        }

        return providers.stream()
                .sorted(Comparator.comparing(LoginProviderView::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        var setupRequired = initializationStateGetter.setupRequired().defaultIfEmpty(false);
        var captchaEnabled = captchas.loginEnabled().defaultIfEmpty(false);
        return Mono.zip(setupRequired, captchaEnabled)
                .map(state -> new LoginStatusView(
                        state.getT1(),
                        securityProperties.getPostLoginUrl(),
                        securitySettings.isRsaLoginEncryptionEnabled(),
                        state.getT2()))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<Authentication> currentAuthentication(ServerRequest request) {
        return request
                .principal()
                .filter(Authentication.class::isInstance)
                .cast(Authentication.class)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext()
                        .mapNotNull(SecurityContext::getAuthentication));
    }

    private boolean isSignedIn(Authentication authentication) {
        return trustResolver.isAuthenticated(authentication)
                && !trustResolver.isAnonymous(authentication)
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    public record SessionView(
            boolean authenticated,
            String username,
            String displayName,
            String avatar,
            String redirectUrl,
            boolean consoleAccessDisallowed,
            List<String> uiPermissions) {
        static SessionView anonymous(String redirectUrl) {
            return new SessionView(false, null, null, null, redirectUrl, false, List.of());
        }
    }

    public record LoginProviderView(
            String name, String displayName, String logo, String authenticationUrl, String helpPage) {}

    public record LoginStatusView(
            boolean setupRequired,
            String postLoginUrl,
            boolean rsaEncryptionEnabled,
            boolean loginCaptchaEnabled) {}
}
