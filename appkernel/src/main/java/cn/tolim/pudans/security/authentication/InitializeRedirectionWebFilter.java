package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import java.net.URI;
import java.util.Set;
import lombok.Getter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class InitializeRedirectionWebFilter implements WebFilter {
    private static final String SETUP_PATH = "/setup";
    private static final String SETUP_ROOT = "/setup/";
    private static final String SETUP_INDEX = "/setup/index.html";
    private static final String CONSOLE_ROOT = "/console/";

    private static final Set<String> REDIRECT_PATHS = Set.of(
            "/",
            SETUP_PATH,
            SETUP_ROOT,
            SETUP_INDEX,
            "/console",
            CONSOLE_ROOT,
            "/uc",
            "/uc/",
            "/login");

    private static final Set<String> SETUP_PATHS = Set.of(SETUP_PATH, SETUP_ROOT, SETUP_INDEX);

    private final URI setupLocation = URI.create(SETUP_ROOT);
    private final URI consoleLocation = URI.create(CONSOLE_ROOT);
    private final ServerWebExchangeMatcher redirectMatcher = exchange ->
            HttpMethod.GET.equals(exchange.getRequest().getMethod())
                            && REDIRECT_PATHS.contains(exchange.getRequest().getPath().value())
                    ? ServerWebExchangeMatcher.MatchResult.match()
                    : ServerWebExchangeMatcher.MatchResult.notMatch();

    private final InitializationStateGetter initializationStateGetter;

    @Getter
    private ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    InitializeRedirectionWebFilter(InitializationStateGetter initializationStateGetter) {
        this.initializationStateGetter = initializationStateGetter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return redirectMatcher.matches(exchange).flatMap(matched -> {
            if (!matched.isMatch()) {
                return chain.filter(exchange);
            }
            var path = exchange.getRequest().getPath().value();
            return initializationStateGetter
                    .setupRequired()
                    .defaultIfEmpty(false)
                    .flatMap(setupRequired -> {
                        if (setupRequired) {
                            if (SETUP_PATHS.contains(path)) {
                                return chain.filter(exchange);
                            }
                            return redirectStrategy.sendRedirect(exchange, setupLocation);
                        }

                        if (SETUP_PATHS.contains(path)) {
                            return redirectStrategy.sendRedirect(exchange, consoleLocation);
                        }
                        return chain.filter(exchange);
                    });
        });
    }

    public void setRedirectStrategy(ServerRedirectStrategy redirectStrategy) {
        Assert.notNull(redirectStrategy, "redirectStrategy cannot be null");
        this.redirectStrategy = redirectStrategy;
    }
}
