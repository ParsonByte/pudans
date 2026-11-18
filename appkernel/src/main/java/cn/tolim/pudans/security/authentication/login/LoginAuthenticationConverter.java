package cn.tolim.pudans.security.authentication.login;

import static java.nio.charset.StandardCharsets.UTF_8;

import cn.tolim.pudans.contract.security.authentication.CryptoService;
import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.core.risk.service.RiskScene;
import cn.tolim.pudans.infra.setting.SecurityParameterSettingService;
import cn.tolim.pudans.infra.utils.IpAddressUtils;
import cn.tolim.pudans.security.authentication.exception.TooManyRequestsException;
import cn.tolim.pudans.security.captcha.CaptchaService;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerFormLoginAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
public class LoginAuthenticationConverter extends ServerFormLoginAuthenticationConverter {

    public static final String ATTEMPTED_USERNAME_ATTRIBUTE =
            LoginAuthenticationConverter.class.getName() + ".USERNAME";

    private final CryptoService cryptoService;

    private final RiskControlService riskControlService;

    private final CaptchaService captchaService;

    private final SecurityParameterSettingService securitySettings;

    public LoginAuthenticationConverter(
            CryptoService cryptoService,
            RiskControlService riskControlService,
            CaptchaService captchaService,
            SecurityParameterSettingService securitySettings) {
        this.cryptoService = cryptoService;
        this.riskControlService = riskControlService;
        this.captchaService = captchaService;
        this.securitySettings = securitySettings;
    }

    private Mono<Boolean> verifyCaptcha(ServerWebExchange exchange) {
        return exchange.getFormData()
                .flatMap(form -> captchaService.verify(
                        form.getFirst("captchaChallengeId"), form.getFirst("captchaAnswer")));
    }

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        var clientIp = IpAddressUtils.getClientIp(exchange.getRequest());
        return captchaService
                .loginEnabled()
                .flatMap(enabled -> Boolean.TRUE.equals(enabled) ? verifyCaptcha(exchange) : Mono.just(true))
                .flatMap(verified -> Boolean.TRUE.equals(verified)
                        ? super.convert(exchange)
                        : Mono.error(new BadCredentialsException("验证码校验失败。")))
                .<Authentication>flatMap(token -> {
                    if (token.getCredentials() == null) {
                        return Mono.error(new BadCredentialsException("凭据不能为空。"));
                    }
                    var credentials = (String) token.getCredentials();
                    if (!securitySettings.isRsaLoginEncryptionEnabled()) {
                        return Mono.just(new UsernamePasswordAuthenticationToken(token.getPrincipal(), credentials));
                    }
                    byte[] credentialsBytes;
                    try {
                        credentialsBytes = Base64.getDecoder().decode(credentials);
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new BadCredentialsException("无效的 Base64 编码。"));
                    }
                    return cryptoService
                            .decrypt(credentialsBytes)
                            .onErrorMap(
                                    InvalidEncryptedMessageException.class,
                                    error -> new BadCredentialsException("无效的凭据。", error))
                            .map(decryptedCredentials -> new UsernamePasswordAuthenticationToken(
                                    token.getPrincipal(), new String(decryptedCredentials, UTF_8)));
                })
                .flatMap(token -> {
                    if (token.getPrincipal() == null) {
                        return Mono.error(new BadCredentialsException("用户名或密码错误。"));
                    }
                    exchange.getAttributes().put(ATTEMPTED_USERNAME_ATTRIBUTE, token.getPrincipal());
                    return riskControlService
                            .evaluate(RiskScene.LOGIN_ATTEMPT, clientIp)
                            .flatMap(decision -> decision.allowed()
                                    ? Mono.just(token)
                                    : Mono.error(new TooManyRequestsException(null)));
                });
    }
}
