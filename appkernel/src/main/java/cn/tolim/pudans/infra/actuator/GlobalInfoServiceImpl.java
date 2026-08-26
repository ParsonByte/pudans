package cn.tolim.pudans.infra.actuator;

import cn.tolim.pudans.contract.infra.ExternalUrlSupplier;
import cn.tolim.pudans.contract.infra.SystemSetting;
import cn.tolim.pudans.infra.setting.SystemConfigFetcher;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
public class GlobalInfoServiceImpl implements GlobalInfoService {

    private final ObjectProvider<SystemConfigFetcher> systemConfigFetcher;

    private final ExternalUrlSupplier externalUrl;

    @Override
    public Mono<GlobalInfo> getGlobalInfo() {
        final var info = new GlobalInfo();
        info.setExternalUrl(externalUrl.getRaw());
        info.setLocale(Locale.getDefault());
        info.setTimeZone(TimeZone.getDefault());
        info.setAllowRegistration(false);
        info.setMustVerifyEmailOnRegistration(false);

        var publishers = new ArrayList<Publisher<?>>(1);
        publishers.add(handleSettings(info));
        return Mono.when(publishers).then(Mono.just(info));
    }

    private Mono<Void> handleSettings(GlobalInfo info) {
        return Optional.ofNullable(systemConfigFetcher.getIfUnique())
                .map(fetcher -> fetcher.getConfig()
                        .doOnNext(config -> handleBasicSetting(info, config))
                        .then())
                .orElseGet(Mono::empty);
    }

    private void handleBasicSetting(GlobalInfo info, Map<String, String> config) {
        var basic = SystemSetting.get(config, SystemSetting.Basic.GROUP, SystemSetting.Basic.class);
        if (basic != null) {
            info.setFavicon(basic.getFavicon());
            info.setSiteTitle(basic.getTitle());
            basic.useSystemLocale().ifPresent(info::setLocale);
        }
    }
}
