package cn.tolim.pudans.security.authentication;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

import java.util.Collections;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.MediaTypeServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;


@Slf4j
@Component
class WebSessionLoginParameterRequestCache implements LoginParameterRequestCache {

    private static final String SESSION_ATTRIBUTE_NAME_PREFIX = WebSessionLoginParameterRequestCache.class + ".";

    private final ServerWebExchangeMatcher cacheMatcher = createDefaultRequestMatcher();

    // 优先从查询参数读取，其次回退到表单数据。
    private static Mono<String> loadParameter(ServerWebExchange exchange, String parameterName) {

        return Mono.justOrEmpty(exchange.getRequest().getQueryParams().getFirst(parameterName))
                .filter(Predicate.not(String::isBlank))
                .switchIfEmpty(Mono.defer(() -> exchange.getFormData()
                        .mapNotNull(form -> form.getFirst(parameterName))
                        .filter(Predicate.not(String::isBlank))));
    }

    // 仅缓存登录相关页面的表单提交请求。
    private static ServerWebExchangeMatcher createDefaultRequestMatcher() {
        var post = pathMatchers(HttpMethod.POST, "/login/**", "/social/**", "/challenges/**");
        var form = new MediaTypeServerWebExchangeMatcher(MediaType.TEXT_HTML);
        form.setIgnoredMediaTypes(Collections.singleton(MediaType.ALL));
        return new AndServerWebExchangeMatcher(post, form);
    }

    // 匹配登录请求时把参数暂存到会话，避免刷新后丢失。
    @Override
    public Mono<Void> saveParameter(ServerWebExchange exchange, String parameterName) {
        return cacheMatcher
                .matches(exchange)
                .filter(ServerWebExchangeMatcher.MatchResult::isMatch)
                .flatMap(m -> loadParameter(exchange, parameterName))
                .delayUntil(parameter -> exchange.getSession()
                        .map(WebSession::getAttributes)
                        .doOnNext(attributes -> {
                            log.debug("保存 {}：{} 到会话", parameterName, parameter);
                            attributes.put(SESSION_ATTRIBUTE_NAME_PREFIX + parameterName, parameter);
                        }))
                .then();
    }

    // 登录后从会话删除暂存参数，防止跨流程残留。
    @Override
    public Mono<Void> removeParameter(ServerWebExchange exchange, String parameterName) {
        return cacheMatcher
                .matches(exchange)
                .filter(ServerWebExchangeMatcher.MatchResult::isMatch)
                .flatMap(m -> exchange.getSession())
                .map(WebSession::getAttributes)
                .doOnNext(attributes -> {
                    var removed = attributes.remove(SESSION_ATTRIBUTE_NAME_PREFIX + parameterName);
                    log.debug("从会话移除 {}：{}", parameterName, removed);
                })
                .then();
    }

    // 从会话读取暂存参数，非字符串值视为不存在。
    @Override
    public Mono<String> getParameter(ServerWebExchange exchange, String parameterName) {
        return exchange.getSession()
                .map(WebSession::getAttributes)
                .mapNotNull(attributes -> {
                    var parameter = attributes.get(SESSION_ATTRIBUTE_NAME_PREFIX + parameterName);
                    log.debug("从会话读取 {}：{}", parameterName, parameter);
                    return parameter;
                })
                .filter(String.class::isInstance)
                .cast(String.class);
    }
}
