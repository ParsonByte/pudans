package cn.tolim.pudans.security.authentication;

import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.ANONYMOUS_AUTHENTICATION;
import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.AUTHENTICATION;
import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.FIRST;
import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.FORM_LOGIN;
import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.HTTP_BASIC;
import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.LAST;
import static org.springframework.security.config.web.server.SecurityWebFiltersOrder.OAUTH2_AUTHORIZATION_CODE;

import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.contract.security.AfterSecurityWebFilter;
import cn.tolim.pudans.contract.security.AnonymousAuthenticationSecurityWebFilter;
import cn.tolim.pudans.contract.security.AuthenticationSecurityWebFilter;
import cn.tolim.pudans.contract.security.BeforeSecurityWebFilter;
import cn.tolim.pudans.contract.security.FormLoginSecurityWebFilter;
import cn.tolim.pudans.contract.security.HttpBasicSecurityWebFilter;
import cn.tolim.pudans.contract.security.OAuth2AuthorizationCodeSecurityWebFilter;
import lombok.Setter;
import org.pf4j.ExtensionPoint;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.WebFilterChainProxy;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component

@Order(100)
public class SecurityWebFiltersConfigurer implements SecurityConfigurer {

    private final ExtensionGetter extensionGetter;

    public SecurityWebFiltersConfigurer(ExtensionGetter extensionGetter) {
        this.extensionGetter = extensionGetter;
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        http.addFilterAt(new SecurityWebFilterChainProxy(BeforeSecurityWebFilter.class), FIRST)
                .addFilterAt(new SecurityWebFilterChainProxy(HttpBasicSecurityWebFilter.class), HTTP_BASIC)
                .addFilterAt(new SecurityWebFilterChainProxy(FormLoginSecurityWebFilter.class), FORM_LOGIN)
                .addFilterAt(new SecurityWebFilterChainProxy(AuthenticationSecurityWebFilter.class), AUTHENTICATION)
                .addFilterAt(
                        new SecurityWebFilterChainProxy(AnonymousAuthenticationSecurityWebFilter.class),
                        ANONYMOUS_AUTHENTICATION)
                .addFilterAt(
                        new SecurityWebFilterChainProxy(OAuth2AuthorizationCodeSecurityWebFilter.class),
                        OAUTH2_AUTHORIZATION_CODE)
                .addFilterAt(new SecurityWebFilterChainProxy(AfterSecurityWebFilter.class), LAST);
    }

    public class SecurityWebFilterChainProxy implements WebFilter {

        private final Class<? extends ExtensionPoint> extensionPointClass;
        @Setter
        private WebFilterChainProxy.WebFilterChainDecorator filterChainDecorator;

        public SecurityWebFilterChainProxy(Class<? extends ExtensionPoint> extensionPointClass) {
            this.extensionPointClass = extensionPointClass;
            this.filterChainDecorator = new WebFilterChainProxy.DefaultWebFilterChainDecorator();
        }

        @Override
        public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
            return extensionGetter
                    .getExtensions(this.extensionPointClass)
                    .sort(AnnotationAwareOrderComparator.INSTANCE)
                    .cast(WebFilter.class)
                    .collectList()
                    .map(filters -> filterChainDecorator.decorate(chain, filters))
                    .flatMap(decoratedChain -> decoratedChain.filter(exchange));
        }
    }
}
