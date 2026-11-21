package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.setting.ExtensionInitializedEvent;
import cn.tolim.pudans.infra.setting.ParameterGroups;
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
public class ConfiguredCaptchas implements Captchas {

    private final ParameterGroups parameterGroups;

    private final Map<String, CaptchaProvider> providers;

    private final CaptchaProvider fallback;

    private final AtomicReference<String> lastWarnedProvider = new AtomicReference<>();

    // 建立提供方索引并要求图片提供方存在
    public ConfiguredCaptchas(
            ParameterGroups parameterGroups, List<CaptchaProvider> providers) {
        this.parameterGroups = parameterGroups;
        this.providers = providers.stream()
                .collect(Collectors.toUnmodifiableMap(CaptchaProvider::type, Function.identity()));
        this.fallback = this.providers.get(ImageCaptchaProvider.TYPE);
        if (fallback == null) {
            throw new IllegalStateException("必须配置图片验证码提供方。");
        }
    }

    // 读取配置后交给对应提供方生成挑战
    @Override
    public Mono<CaptchaChallenge> newChallenge() {
        return parameterGroups
                .getCaptcha()
                .defaultIfEmpty(new ParameterSetting.Captcha())
                .flatMap(config -> resolve(config.getCaptchaProvider()).challenge(config));
    }

    // 读取配置后交给对应提供方校验答案
    @Override
    public Mono<Boolean> verify(String challengeId, String answer) {
        return parameterGroups
                .getCaptcha()
                .defaultIfEmpty(new ParameterSetting.Captcha())
                .flatMap(config -> resolve(config.getCaptchaProvider()).verify(challengeId, answer));
    }

    // 未配置时默认关闭登录验证码
    @Override
    public Mono<Boolean> loginEnabled() {
        return parameterGroups
                .getCaptcha()
                .map(config -> Boolean.TRUE.equals(config.getLoginCaptchaEnabled()))
                .defaultIfEmpty(false);
    }

    // 未配置时默认关闭找回验证码
    @Override
    public Mono<Boolean> recoveryEnabled() {
        return parameterGroups
                .getCaptcha()
                .map(config -> Boolean.TRUE.equals(config.getRecoveryCaptchaEnabled()))
                .defaultIfEmpty(false);
    }

    // 扩展初始化后提前校验提供方配置
    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        parameterGroups.getCaptcha().subscribe(
                config -> resolve(config.getCaptchaProvider()),
                error -> log.warn("无法校验验证码提供方配置", error));
    }

    // 未知提供方回退图片实现并限制告警频率
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
