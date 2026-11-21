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

    public ParameterSettingExternalUrlSupplier(
            PudansProperties pudansProperties,
            WebFluxProperties webFluxProperties,
            ParameterSettingFetcher systemConfigFetcher) {
        this.pudansProperties = pudansProperties;
        this.webFluxProperties = webFluxProperties;
        this.systemConfigFetcher = systemConfigFetcher;
    }

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

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        refetchExternalUrl().ifPresent(externalUrl -> this.externalUrl = externalUrl);
    }

    @EventListener
    void onExternalUrlChanged(ExternalUrlChangedEvent event) {
        this.externalUrl = normalizeUrl(event.getExternalUrl());
    }

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

    @Nullable
    @Override
    public URL getRaw() {
        return normalizeUrl(externalUrl != null ? externalUrl : pudansProperties.getExternalUrl());
    }

    private String getBasePath() {
        var basePath = webFluxProperties.getBasePath();
        if (!StringUtils.hasText(basePath)) {
            basePath = "/";
        }
        return basePath;
    }
}
