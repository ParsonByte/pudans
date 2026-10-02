package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.core.risk.service.RiskScene;
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

    private final RiskControlService riskControlService;

    public TwoFactorCodeAuthenticationConverter(RiskControlService riskControlService) {
        this.riskControlService = riskControlService;
    }

    private static String resolveType(ServerWebExchange exchange) {
        var segments = exchange.getRequest().getPath().pathWithinApplication().value().split("/");
        for (var i = segments.length - 1; i >= 0; i--) {
            if (!segments[i].isBlank()) {
                return segments[i];
            }
        }
        return DEFAULT_TYPE;
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(TwoFactorAuthentication.class::isInstance)
                .switchIfEmpty(Mono.error(() -> new TwoFactorAuthException("MFA Authentication required.")))
                .flatMap(authentication -> exchange.getFormData()
                        .<Authentication>handle((formData, sink) -> {
                            var code = formData.getFirst(CODE_PARAMETER);
                            if (StringUtils.isBlank(code)) {
                                sink.error(new TwoFactorAuthException("Empty code parameter."));
                                return;
                            }
                            sink.next(new TwoFactorAuthenticationToken(resolveType(exchange), code));
                        })
                        .flatMap(riskGate(exchange)));
    }

    private Function<Authentication, Mono<Authentication>> riskGate(ServerWebExchange exchange) {
        return authentication -> exchange.getSession()
                .map(session -> session.getId())
                .defaultIfEmpty(IpAddressUtils.getClientIp(exchange.getRequest()))
                .flatMap(subject -> riskControlService.isBanned(RiskScene.TOTP_ATTEMPT, subject))
                .flatMap(banned -> Boolean.TRUE.equals(banned)
                        ? Mono.error(new TooManyRequestsException(null))
                        : Mono.just(authentication));
    }
}
