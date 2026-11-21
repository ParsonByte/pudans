package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.contract.extension.ApiGroups;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.http.codec.HttpMessageReader;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


class SudoModeWebFilter implements WebFilter {

    static final String SUDO_PATH = "/sudo";
    static final String SUDO_CODE_PATH = "/sudo/code";
    static final String SUDO_CONFIRM_PATH = "/sudo/confirm";

    private final SudoChallenges sudoChallenges;
    private final ServerResponse.Context responseContext;
    private final List<HttpMessageReader<?>> messageReaders;
    private final RouterFunction<ServerResponse> sudoRoutes;
    private final ServerWebExchangeMatcher sudoProtocolMatcher;
    private final ServerWebExchangeMatcher sensitiveMatcher;

    SudoModeWebFilter(SudoChallenges sudoChallenges, ServerResponse.Context responseContext) {
        this.sudoChallenges = sudoChallenges;
        this.responseContext = responseContext;
        this.messageReaders = HandlerStrategies.withDefaults().messageReaders();
        this.sudoRoutes = RouterFunctions.route()
                .GET(SUDO_PATH, this::getStatus)
                .POST(SUDO_CODE_PATH, this::sendCode)
                .POST(SUDO_CONFIRM_PATH, this::confirm)
                .build();
        this.sudoProtocolMatcher = new OrServerWebExchangeMatcher(
                pathMatchers(HttpMethod.GET, SUDO_PATH),
                pathMatchers(HttpMethod.POST, SUDO_CODE_PATH, SUDO_CONFIRM_PATH));
        this.sensitiveMatcher = createSensitiveMatcher();
    }

    private static String requiredFormValue(MultiValueMap<String, String> form, String name) {
        var value = form.getFirst(name);
        if (!StringUtils.hasText(value)) {
            throw new ServerWebInputException(name + " 为必填项");
        }
        return value;
    }

    private static ServerWebExchangeMatcher createSensitiveMatcher() {
        return new OrServerWebExchangeMatcher(
                pathMatchers(
                        HttpMethod.PUT,
                        ApiGroups.API_PREFIX + "/users/-/password",
                        ApiGroups.API_PREFIX + "/users/*/password",
                        ApiGroups.API_PREFIX + "/users/-/self/password"),
                pathMatchers(
                        HttpMethod.POST,
                        ApiGroups.API_PREFIX + "/personalaccesstokens"),
                pathMatchers(
                        HttpMethod.DELETE,
                        ApiGroups.API_PREFIX + "/personalaccesstokens/*"),
                pathMatchers(
                        HttpMethod.PUT,
                        ApiGroups.API_PREFIX + "/personalaccesstokens/*/actions/revocation",
                        ApiGroups.API_PREFIX + "/personalaccesstokens/*/actions/restoration"));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return sudoProtocolMatcher.matches(exchange).flatMap(sudoProtocol -> {
            if (sudoProtocol.isMatch()) {
                return handleSudoProtocol(exchange);
            }
            return sensitiveMatcher.matches(exchange).flatMap(sensitive -> {
                if (!sensitive.isMatch()) {
                    return chain.filter(exchange);
                }
                return enforceSudo(exchange).then(Mono.defer(() -> chain.filter(exchange)));
            });
        });
    }

    private Mono<Void> handleSudoProtocol(ServerWebExchange exchange) {
        var request = ServerRequest.create(exchange, messageReaders);
        return sudoRoutes
                .route(request)
                .flatMap(handler -> handler.handle(request))
                .flatMap(response -> response.writeTo(exchange, responseContext));
    }

    private Mono<ServerResponse> getStatus(ServerRequest request) {
        return sudoChallenges
                .status(request.exchange())
                .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    private Mono<ServerResponse> sendCode(ServerRequest request) {
        return request.formData()
                .map(form -> requiredFormValue(form, "method"))
                .flatMap(sudoChallenges::sendCode)
                .then(ServerResponse.noContent().build());
    }

    private Mono<ServerResponse> confirm(ServerRequest request) {
        return request.formData()
                .flatMap(form -> {
                    var method = requiredFormValue(form, "method");
                    var code = requiredFormValue(form, "code");
                    return sudoChallenges.confirm(method, code, request.exchange());
                })
                .then(ServerResponse.noContent().build());
    }

    private Mono<Void> enforceSudo(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                // 令牌认证同样需要安全确认，避免持令牌绕过 sudo 直接改密或管理令牌
                .filter(authentication -> authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken))
                .flatMap(auth -> sudoChallenges.requireSudo(exchange, auth.getName()));
    }
}
