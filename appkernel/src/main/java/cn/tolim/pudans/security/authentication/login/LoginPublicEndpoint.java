package cn.tolim.pudans.security.authentication.login;

import cn.tolim.pudans.contract.core.extension.AuthProvider;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.core.extension.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.extension.GroupVersion;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.security.authentication.AuthProviderService;
import java.util.Comparator;
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

/**
 * Public contract consumed by the auth application: the current session, the
 * enabled OAuth2 providers and the first-run state of the instance.
 */
@Component
@RequiredArgsConstructor
public class LoginPublicEndpoint implements CustomEndpoint {

    private final SecurityProperties securityProperties;

    private final UserService userService;

    private final AuthProviderService authProviderService;

    private final InitializationStateGetter initializationStateGetter;

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.AUTH + "/v1alpha1");
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        final var tag = "AuthV1alpha1Login";
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
                .flatMap(authentication -> userService
                        .getUser(authentication.getName())
                        .map(user -> new SessionView(
                                true,
                                authentication.getName(),
                                user.getSpec().getDisplayName(),
                                user.getSpec().getAvatar(),
                                securityProperties.getPostLoginUrl()))
                        .defaultIfEmpty(new SessionView(
                                true,
                                authentication.getName(),
                                authentication.getName(),
                                null,
                                securityProperties.getPostLoginUrl())))
                .switchIfEmpty(Mono.just(SessionView.anonymous(securityProperties.getPostLoginUrl())))
                .flatMap(view -> ServerResponse.ok().bodyValue(view));
    }

    private Mono<ServerResponse> providers(ServerRequest request) {
        return authProviderService
                .getEnabledProviders()
                .filter(provider -> !AuthProvider.AuthType.FORM.equals(provider.getSpec().getAuthType()))
                .filter(provider -> StringUtils.hasText(provider.getSpec().getAuthenticationUrl()))
                .map(provider -> new LoginProviderView(
                        provider.getMetadata().getName(),
                        provider.getSpec().getDisplayName(),
                        provider.getSpec().getLogo(),
                        provider.getSpec().getAuthenticationUrl(),
                        provider.getSpec().getHelpPage()))
                .sort(Comparator.comparing(LoginProviderView::displayName, String.CASE_INSENSITIVE_ORDER))
                .collectList()
                .flatMap(views -> ServerResponse.ok().bodyValue(views));
    }

    private Mono<ServerResponse> status(ServerRequest request) {
        return initializationStateGetter
                .setupRequired()
                .defaultIfEmpty(false)
                .map(setupRequired -> new LoginStatusView(setupRequired, securityProperties.getPostLoginUrl()))
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
            boolean authenticated, String username, String displayName, String avatar, String redirectUrl) {
        static SessionView anonymous(String redirectUrl) {
            return new SessionView(false, null, null, null, redirectUrl);
        }
    }

    public record LoginProviderView(
            String name, String displayName, String logo, String authenticationUrl, String helpPage) {}

    public record LoginStatusView(boolean setupRequired, String postLoginUrl) {}
}
