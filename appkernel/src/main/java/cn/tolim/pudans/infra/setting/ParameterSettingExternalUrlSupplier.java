package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.contract.ExternalUrlSupplier;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.ExternalUrlUtils;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.Exceptions;


@Slf4j
@Component
class ParameterSettingExternalUrlSupplier implements ExternalUrlSupplier {

    private final PudansProperties pudansProperties;

    private final WebFluxProperties webFluxProperties;

    private final ParameterSettingFetcher systemConfigFetcher;

    @Nullable
    private URL externalUrl;

    // 注入配置读取与属性，供外部地址解析。
    public ParameterSettingExternalUrlSupplier(
            PudansProperties pudansProperties,
            WebFluxProperties webFluxProperties,
            ParameterSettingFetcher systemConfigFetcher) {
        this.pudansProperties = pudansProperties;
        this.webFluxProperties = webFluxProperties;
        this.systemConfigFetcher = systemConfigFetcher;
    }

    // 将外部地址转换为标准字符形式，失败时保留原值。
    @Nullable
    private static URL normalizeUrl(@Nullable URL url) {
        if (url == null) {
            return null;
        }
        var externalUrlString = url.toString();
        var normalizedUrlString = ExternalUrlUtils.toAscii(externalUrlString);
        if (normalizedUrlString.equals(externalUrlString)) {
            return url;
        }
        try {
            return URI.create(normalizedUrlString).toURL();
        } catch (IllegalArgumentException | MalformedURLException e) {

            log.debug("无法规范化外部 URL：{}", externalUrlString, e);
            return url;
        }
    }

    // 扩展初始化后重新读取站点外部地址。
    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        refetchExternalUrl().ifPresent(externalUrl -> this.externalUrl = externalUrl);
    }

    // 立即更新缓存的外部地址并规范化。
    @EventListener
    void onExternalUrlChanged(ExternalUrlChangedEvent event) {
        this.externalUrl = normalizeUrl(event.getExternalUrl());
    }

    // 优先读取站点地址，回退到基础配置中的地址。
    Optional<URL> refetchExternalUrl() {
        return systemConfigFetcher
                .fetch(ParameterSetting.Site.GROUP, ParameterSetting.Site.class)
                .mapNotNull(ParameterSetting.Site::getSiteUrl)
                .filter(StringUtils::hasText)
                .switchIfEmpty(systemConfigFetcher
                        .getBasic()
                        .mapNotNull(ParameterSetting.Basic::getExternalUrl))
                .filter(StringUtils::hasText)
                .mapNotNull(externalUrlString -> {
                    try {
                        return URI.create(ExternalUrlUtils.toAscii(externalUrlString))
                                .toURL();
                    } catch (MalformedURLException e) {
                        log.error("""
                                无法从参数设置解析外部 URL {}，回退到属性中的默认外部 URL 提供器。\
                                """, externalUrlString, e);

                        return null;
                    }
                })
                .blockOptional(Duration.ofSeconds(10));
    }

    // 按绝对链接配置决定返回外部地址还是基础路径。
    @Override
    public URI get() {
        try {
            if (!pudansProperties.isUseAbsolutePermalink()) {
                return URI.create(getBasePath());
            }
            if (externalUrl != null) {
                return normalizeUrl(externalUrl).toURI();
            }
            return normalizeUrl(pudansProperties.getExternalUrl()).toURI();
        } catch (URISyntaxException e) {
            throw Exceptions.propagate(e);
        }
    }

    // 依次使用已配置地址、属性地址与请求地址。
    @Override
    public URL getURL(HttpRequest request) {
        if (this.externalUrl != null) {
            return normalizeUrl(this.externalUrl);
        }
        var url = normalizeUrl(pudansProperties.getExternalUrl());
        if (url != null) {
            return url;
        }
        try {
            url = normalizeUrl(request.getURI().resolve(getBasePath()).toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException("无法将请求 URI 解析为 URL。", e);
        }
        return url;
    }

    // 返回规范化后的原始外部地址，可能为空。
    @Nullable
    @Override
    public URL getRaw() {
        return normalizeUrl(externalUrl != null ? externalUrl : pudansProperties.getExternalUrl());
    }

    // 返回配置的基础路径，未配置时使用根路径。
    private String getBasePath() {
        var basePath = webFluxProperties.getBasePath();
        if (!StringUtils.hasText(basePath)) {
            basePath = "/";
        }
        return basePath;
    }
}
