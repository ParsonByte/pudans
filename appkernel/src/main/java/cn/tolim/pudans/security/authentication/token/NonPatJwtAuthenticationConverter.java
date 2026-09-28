package cn.tolim.pudans.security.authentication.token;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import reactor.core.publisher.Mono;


@Slf4j
record NonPatJwtAuthenticationConverter(Converter<Jwt, Mono<AbstractAuthenticationToken>> delegate)
        implements Converter<Jwt, Mono<AbstractAuthenticationToken>> {

    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {

        if (jwt.getClaims().containsKey("pat_name")) {
            log.warn(
                    "Skip JWT authentication for PAT token: {}", jwt.getClaims().get("pat_name"));
            return Mono.error(new InvalidBearerTokenException("PAT token is not allowed here"));
        }
        return delegate.convert(jwt);
    }
}
