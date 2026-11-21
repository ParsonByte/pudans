package cn.tolim.pudans.infra.actuator;

import cn.tolim.pudans.infra.contract.ExternalUrlSupplier;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class ConfiguredSiteInfo implements SiteInfo {

    private final ObjectProvider<ParameterSettingFetcher> parameterSettingFetcher;

    private final ExternalUrlSupplier externalUrl;

    // 优先采用站点级配置，缺失时回退到基础配置。
    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    // 汇总站点、基础与服务器设置，输出对外公开的面板信息。
    @Override
    public Mono<GlobalInfo> getGlobalInfo() {
        final var info = new GlobalInfo();
        info.setExternalUrl(externalUrl.getRaw());
        info.setTimeZone(TimeZone.getTimeZone(PudansTime.BEIJING));
        info.setAllowRegistration(false);
        info.setMustVerifyEmailOnRegistration(false);

        var publishers = new ArrayList<Publisher<?>>(1);
        publishers.add(handleSettings(info));
        return Mono.when(publishers).then(Mono.just(info));
    }

    // 参数设置器可能尚未就绪，缺失时保留默认值。
    private Mono<Void> handleSettings(GlobalInfo info) {
        return Optional.ofNullable(parameterSettingFetcher.getIfUnique())
                .map(fetcher -> Mono.zip(
                                fetcher.fetch(ParameterSetting.Site.GROUP, ParameterSetting.Site.class)
                                        .defaultIfEmpty(new ParameterSetting.Site()),
                                fetcher.fetch(ParameterSetting.Basic.GROUP, ParameterSetting.Basic.class)
                                        .defaultIfEmpty(new ParameterSetting.Basic()),
                                fetcher.fetch(ParameterSetting.Server.GROUP, ParameterSetting.Server.class)
                                        .defaultIfEmpty(new ParameterSetting.Server()))
                        .doOnNext(tuple -> {
                            applySite(info, tuple.getT1(), tuple.getT2());
                            info.setSiteId(tuple.getT3().getSiteId());
                        })
                        .then())
                .orElseGet(Mono::empty);
    }

    // 站点字段按站点配置优先、基础配置兜底的顺序取值。
    private void applySite(GlobalInfo info, ParameterSetting.Site site, ParameterSetting.Basic basic) {
        info.setFavicon(firstText(site.getFavicon(), basic.getFavicon()));
        info.setSiteTitle(firstText(site.getSiteName(), basic.getTitle()));
        info.setSiteDescription(firstText(site.getSiteDescription(), basic.getSubtitle()));
        info.setLogoDark(firstText(site.getLogoDark(), basic.getLogo()));
        info.setLogoLight(firstText(site.getLogoLight(), basic.getLogo()));
    }
}
