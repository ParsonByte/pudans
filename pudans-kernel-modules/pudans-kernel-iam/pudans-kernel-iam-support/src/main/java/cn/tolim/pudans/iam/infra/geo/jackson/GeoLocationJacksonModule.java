package cn.tolim.pudans.iam.infra.geo.jackson;

import cn.tolim.pudans.iam.infra.geo.GeoLocation;
import cn.tolim.pudans.iam.infra.geo.GeoLocationProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

public class GeoLocationJacksonModule extends SimpleModule {
    public GeoLocationJacksonModule() {
        super("PudansGeoLocationModule");
        setMixInAnnotation(GeoLocation.class, GeoLocationMixin.class);
        setMixInAnnotation(GeoLocationProvider.class, GeoLocationProviderMixin.class);
    }

    private interface GeoLocationMixin {
    }

    private interface GeoLocationProviderMixin {
    }
}
