package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.extension.contract.ApiGroups;

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
    private final ServerWebExchangeMatcher passwordMatcher;
    private final ServerWebExchangeMatcher sensitiveMatcher;

    // 构建安全确认路由与敏感操作匹配器
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
        this.passwordMatcher = createPasswordMatcher();
        this.sensitiveMatcher = new OrServerWebExchangeMatcher(passwordMatcher, createTokenMatcher());
    }

    // 读取必填表单字段，缺失时拒绝请求
    private static String requiredFormValue(MultiValueMap<String, String> form, String name) {
        var value = form.getFirst(name);
        if (!StringUtils.hasText(value)) {
            throw new ServerWebInputException(name + " 为必填项");
        }
        return value;
    }

    // 定义需要安全确认的改密与令牌接口
    private static ServerWebExchangeMatcher createPasswordMatcher() {
        return pathMatchers(
                HttpMethod.PUT,
                ApiGroups.API_PREFIX + "/users/-/password",
                ApiGroups.API_PREFIX + "/users/*/password",
                ApiGroups.API_PREFIX + "/users/-/self/password");
    }

    // 定义必须完成安全确认的令牌管理接口
    private static ServerWebExchangeMatcher createTokenMatcher() {
        return new OrServerWebExchangeMatcher(
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

    // 拦截安全确认协议并对敏感操作强制执行
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
                // 改密接口允许无验证方式的用户设置初始密码，令牌接口必须完成确认。
                return passwordMatcher
                        .matches(exchange)
                        .flatMap(passwordPath -> enforceSudo(exchange, passwordPath.isMatch())
                                .then(Mono.defer(() -> chain.filter(exchange))));
            });
        });
    }

    // 将安全确认请求分发给内部路由处理
    private Mono<Void> handleSudoProtocol(ServerWebExchange exchange) {
        var request = ServerRequest.create(exchange, messageReaders);
        return sudoRoutes
                .route(request)
                .flatMap(handler -> handler.handle(request))
                .flatMap(response -> response.writeTo(exchange, responseContext));
    }

    // 返回当前会话的安全确认状态
    private Mono<ServerResponse> getStatus(ServerRequest request) {
        return sudoChallenges
                .status(request.exchange())
                .flatMap(status -> ServerResponse.ok().bodyValue(status));
    }

    // 读取验证方式并触发验证码发送
    private Mono<ServerResponse> sendCode(ServerRequest request) {
        return request.formData()
                .map(form -> requiredFormValue(form, "method"))
                .flatMap(sudoChallenges::sendCode)
                .then(ServerResponse.noContent().build());
    }

    // 读取必要参数并完成安全确认
    private Mono<ServerResponse> confirm(ServerRequest request) {
        return request.formData()
                .flatMap(form -> {
                    var method = requiredFormValue(form, "method");
                    var code = requiredFormValue(form, "code");
                    return sudoChallenges.confirm(method, code, request.exchange());
                })
                .then(ServerResponse.noContent().build());
    }

    // 对已认证用户校验安全确认是否有效
    private Mono<Void> enforceSudo(ServerWebExchange exchange, boolean allowWithoutMethods) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                // 令牌认证同样需要安全确认，避免持令牌绕过 sudo 直接改密或管理令牌
                .filter(authentication -> authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken))
                .flatMap(auth ->
                        sudoChallenges.requireSudo(exchange, auth.getName(), allowWithoutMethods));
    }
}
