package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.core.risk.RiskScene;
import cn.tolim.pudans.infra.utils.IpAddressUtils;
import cn.tolim.pudans.security.authentication.exception.TooManyRequestsException;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import java.util.function.Function;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class TwoFactorCodeAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String CODE_PARAMETER = "code";

    private static final String DEFAULT_TYPE = "totp";

    private final RiskGuard riskGuard;

    // 注入风控守卫，在解析验证码前限制暴力尝试。
    public TwoFactorCodeAuthenticationConverter(RiskGuard riskGuard) {
        this.riskGuard = riskGuard;
    }

    // 取路径最后一段作为验证方式，缺省回退到 TOTP。
    private static String resolveType(ServerWebExchange exchange) {
        var segments = exchange.getRequest().getPath().pathWithinApplication().value().split("/");
        for (var i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isBlank()) {
                return segments[i];
            }
        }
        return DEFAULT_TYPE;
    }

    // 从表单读取验证码并通过风控后生成待认证令牌。
    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(TwoFactorAuthentication.class::isInstance)
                .switchIfEmpty(Mono.error(() -> new TwoFactorAuthException("需要完成多重身份验证。")))
                .flatMap(authentication -> exchange.getFormData()
                        .<Authentication>handle((formData, sink) -> {
                            var code = formData.getFirst(CODE_PARAMETER);
                            if (StringUtils.isBlank(code)) {
                                sink.error(new TwoFactorAuthException("验证码参数为空。"));
                                return;
                            }
                            sink.next(new TwoFactorAuthenticationToken(resolveType(exchange), code));
                        })
                        .flatMap(riskGate(exchange)));
    }

    // 以会话或客户端地址为维度拦截被风控禁止的尝试。
    private Function<Authentication, Mono<Authentication>> riskGate(ServerWebExchange exchange) {
        return authentication -> exchange.getSession()
                .map(session -> session.getId())
                .defaultIfEmpty(IpAddressUtils.getClientIp(exchange.getRequest()))
                .flatMap(subject -> riskGuard.isBanned(RiskScene.TOTP_ATTEMPT, subject))
                .flatMap(banned -> Boolean.TRUE.equals(banned)
                        ? Mono.error(new TooManyRequestsException(null))
                        : Mono.just(authentication));
    }
}
