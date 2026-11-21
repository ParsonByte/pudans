package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.api.ExternalUrlSupplier;
import cn.tolim.pudans.infra.api.ParameterInfoGetter;
import cn.tolim.pudans.infra.api.ParameterSetting;
import cn.tolim.pudans.infra.api.SystemInfo;
import cn.tolim.pudans.infra.api.SystemVersionSupplier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
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
public class ParameterInfoGetterImpl implements ParameterInfoGetter {
    private final ParameterSettingFetcher environmentFetcher;
    private final SystemVersionSupplier systemVersionSupplier;
    private final ExternalUrlSupplier externalUrlSupplier;
    private final ServerProperties serverProperties;
    private final WebFluxProperties webFluxProperties;

    private static String firstText(String first, String second) {
        return StringUtils.isNotBlank(first) ? first : second;
    }

    @Override
    public Mono<SystemInfo> get() {
        var systemInfo = new SystemInfo()
                .setVersion(systemVersionSupplier.get())
                .setUrl(getExternalUrl())
                .setTimeZone(TimeZone.getDefault());

        var siteMono = environmentFetcher
                .fetch(ParameterSetting.Site.GROUP, ParameterSetting.Site.class)
                .defaultIfEmpty(new ParameterSetting.Site());
        var basicMono = environmentFetcher
                .fetch(ParameterSetting.Basic.GROUP, ParameterSetting.Basic.class)
                .defaultIfEmpty(new ParameterSetting.Basic());
        var seoMono = environmentFetcher
                .fetch(ParameterSetting.Seo.GROUP, ParameterSetting.Seo.class)
                .defaultIfEmpty(new ParameterSetting.Seo());
        var serverMono = environmentFetcher
                .fetch(ParameterSetting.Server.GROUP, ParameterSetting.Server.class)
                .defaultIfEmpty(new ParameterSetting.Server());

        return Mono.zip(siteMono, basicMono, seoMono, serverMono)
                .doOnNext(tuple -> {
                    var site = tuple.getT1();
                    var basic = tuple.getT2();
                    var seo = tuple.getT3();
                    var server = tuple.getT4();
                    systemInfo
                            .setTitle(firstText(site.getSiteName(), basic.getTitle()))
                            .setSubtitle(firstText(site.getSiteDescription(), basic.getSubtitle()))
                            .setLogo(firstText(site.getLogoDark(), firstText(site.getLogoLight(), basic.getLogo())))
                            .setFavicon(firstText(site.getFavicon(), basic.getFavicon()))
                            .setSiteId(server.getSiteId())
                            .setSeo(new SystemInfo.SeoProp()
                                    .setBlockSpiders(BooleanUtils.isTrue(seo.getBlockSpiders()))
                                    .setKeywords(seo.getKeywords())
                                    .setDescription(seo.getDescription()));
                })
                .thenReturn(systemInfo);
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

            throw new RuntimeException("无法根据服务器属性创建 URL。", e);
        }
    }
}
