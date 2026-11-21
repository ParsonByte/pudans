package cn.tolim.pudans.security.authentication.oauth2;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class WebSessionOAuth2BindIntent implements OAuth2BindIntent {

    private static final String IDENTITY_ATTRIBUTE_KEY = OAuth2BindIntent.class + ".IDENTITY";
    private static final String CREATED_AT_ATTRIBUTE_KEY = OAuth2BindIntent.class + ".CREATED_AT";
    private static final Duration MAX_AGE = Duration.ofMinutes(5);

    private Clock clock = Clock.systemUTC();

    @Override
    public Mono<String> consume(ServerWebExchange exchange) {
        return exchange.getSession().flatMap(session -> {
            var identity = session.getAttributes().remove(IDENTITY_ATTRIBUTE_KEY);
            var createdAt = session.getAttributes().remove(CREATED_AT_ATTRIBUTE_KEY);
            if (identity instanceof String value
                    && createdAt instanceof Number epochMilli
                    && !Instant.ofEpochMilli(epochMilli.longValue())
                    .plus(MAX_AGE)
                    .isBefore(clock.instant())) {
                return Mono.just(value);
            }
            return Mono.empty();
        });
    }

    @Override
    public Mono<Void> clear(ServerWebExchange exchange) {
        return exchange.getSession()
                .doOnNext(session -> {
                    session.getAttributes().remove(IDENTITY_ATTRIBUTE_KEY);
                    session.getAttributes().remove(CREATED_AT_ATTRIBUTE_KEY);
                })
                .then();
    }
}
