package cn.tolim.pudans.infra.webfilter;

import java.util.Locale;
import java.util.Objects;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class LocaleChangeWebFilter implements WebFilter {

    public static final String LANGUAGE_COOKIE_NAME = "language";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.defer(() -> {
            var locale = exchange.getLocaleContext().getLocale();
            if (locale == null) {
                var language = exchange.getRequest().getQueryParams().getFirst("language");
                locale = language == null ? null : Locale.forLanguageTag(language);
            }
            if (locale != null) {
                setLanguageCookieIfAbsent(exchange, locale);
            }
            return chain.filter(exchange);
        });
    }

    void setLanguageCookieIfAbsent(ServerWebExchange exchange, Locale locale) {
        var languageCookie = exchange.getRequest().getCookies().getFirst(LANGUAGE_COOKIE_NAME);
        if (languageCookie != null && Objects.equals(languageCookie.getValue(), locale.toLanguageTag())) {
            return;
        }
        var cookie = ResponseCookie.from(LANGUAGE_COOKIE_NAME, locale.toLanguageTag())
                .path("/")
                .secure("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme()))
                .sameSite("Lax")
                .build();
        exchange.getResponse().addCookie(cookie);
        exchange.getResponse().getHeaders().add(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
    }
}
