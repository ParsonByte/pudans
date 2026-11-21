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

    // 拒绝个人访问令牌走签名令牌路径，其余交给原转换器。
    @Override
    public Mono<AbstractAuthenticationToken> convert(Jwt jwt) {

        if (jwt.getClaims().containsKey("pat_name")) {
            log.warn(
                    "跳过 PAT 令牌的 JWT 认证：{}", jwt.getClaims().get("pat_name"));
            return Mono.error(new InvalidBearerTokenException("此处不允许使用 PAT 令牌"));
        }
        return delegate.convert(jwt);
    }
}
