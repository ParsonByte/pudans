package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.infra.setting.ExtensionInitializedEvent;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ConfigurableCaptchaService implements CaptchaService {

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final Map<String, CaptchaProvider> providers;

    private final CaptchaProvider fallback;

    private final AtomicReference<String> lastWarnedProvider = new AtomicReference<>();

    public ConfigurableCaptchaService(
            ParameterSettingGroupService parameterSettingGroupService, List<CaptchaProvider> providers) {
        this.parameterSettingGroupService = parameterSettingGroupService;
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(CaptchaProvider::type, Function.identity()));
        this.fallback = this.providers.get(ImageCaptchaProvider.TYPE);
        if (fallback == null) {
            throw new IllegalStateException("必须配置图片验证码提供方。");
        }
    }

    @Override
    public Mono<CaptchaChallenge> newChallenge() {
        return parameterSettingGroupService
                .getCaptcha()
                .defaultIfEmpty(new ParameterSetting.Captcha())
                .flatMap(config -> resolve(config.getCaptchaProvider()).challenge(config));
    }

    @Override
    public Mono<Boolean> verify(String challengeId, String answer) {
        return parameterSettingGroupService
                .getCaptcha()
                .defaultIfEmpty(new ParameterSetting.Captcha())
                .flatMap(config -> resolve(config.getCaptchaProvider()).verify(challengeId, answer));
    }

    @Override
    public Mono<Boolean> loginEnabled() {
        return parameterSettingGroupService
                .getCaptcha()
                .map(config -> Boolean.TRUE.equals(config.getLoginCaptchaEnabled()))
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<Boolean> recoveryEnabled() {
        return parameterSettingGroupService
                .getCaptcha()
                .map(config -> Boolean.TRUE.equals(config.getRecoveryCaptchaEnabled()))
                .defaultIfEmpty(false);
    }

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        parameterSettingGroupService.getCaptcha().subscribe(
                config -> resolve(config.getCaptchaProvider()),
                error -> log.warn("无法校验验证码提供方配置", error));
    }

    CaptchaProvider resolve(String type) {
        if (!StringUtils.hasText(type)) {
            return fallback;
        }
        var provider = providers.get(type.trim().toLowerCase(Locale.ROOT));
        if (provider != null) {
            return provider;
        }
        if (!type.equals(lastWarnedProvider.get())) {
            lastWarnedProvider.set(type);
            log.warn("未知的验证码提供方 [{}]；回退到 [{}]", type, ImageCaptchaProvider.TYPE);
        }
        return fallback;
    }
}
