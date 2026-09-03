package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.infra.ExternalUrlSupplier;
import cn.tolim.pudans.contract.infra.SystemInfo;
import cn.tolim.pudans.contract.infra.SystemInfoGetter;
import cn.tolim.pudans.contract.infra.SystemSetting;
import cn.tolim.pudans.contract.infra.SystemVersionSupplier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Locale;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.webflux.autoconfigure.WebFluxProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class SystemInfoGetterImpl implements SystemInfoGetter {
    private final SystemConfigFetcher environmentFetcher;
    private final SystemVersionSupplier systemVersionSupplier;
    private final ExternalUrlSupplier externalUrlSupplier;
    private final ServerProperties serverProperties;
    private final WebFluxProperties webFluxProperties;

    @Override
    public Mono<SystemInfo> get() {
        var systemInfo = new SystemInfo()
                .setVersion(systemVersionSupplier.get())
                .setUrl(getExternalUrl())

                .setLocale(Locale.getDefault())
                .setTimeZone(TimeZone.getDefault());

        var basicMono = environmentFetcher
                .fetch(SystemSetting.Basic.GROUP, SystemSetting.Basic.class)
                .doOnNext(basic -> systemInfo
                        .setTitle(basic.getTitle())
                        .setSubtitle(basic.getSubtitle())
                        .setLogo(basic.getLogo())
                        .setFavicon(basic.getFavicon()));

        var seoMono = environmentFetcher
                .fetch(SystemSetting.Seo.GROUP, SystemSetting.Seo.class)
                .doOnNext(seo -> systemInfo.setSeo(new SystemInfo.SeoProp()
                        .setBlockSpiders(BooleanUtils.isTrue(seo.getBlockSpiders()))
                        .setKeywords(seo.getKeywords())
                        .setDescription(seo.getDescription())));

        return Mono.when(basicMono, seoMono).thenReturn(systemInfo);
    }

    private URL getExternalUrl() {
        var url = externalUrlSupplier.getRaw();
        if (url != null) {
            return url;
        }
        var port = serverProperties.getPort();
        var basePath = StringUtils.defaultIfBlank(webFluxProperties.getBasePath(), "/");
        try {
            var uriStr = "http://localhost:" + port + basePath;
            return URI.create(StringUtils.removeEnd(uriStr, "/")).toURL();
        } catch (MalformedURLException e) {

            throw new RuntimeException("Cannot create URL from server properties.", e);
        }
    }
}
