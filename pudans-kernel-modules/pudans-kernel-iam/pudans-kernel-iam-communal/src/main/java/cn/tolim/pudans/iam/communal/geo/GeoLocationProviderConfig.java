package cn.tolim.pudans.iam.communal.geo;

import cn.tolim.pudans.iam.infra.geo.GeoLocationProvider;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Data
public class GeoLocationProviderConfig {

    // Platform
    @NotNull(message = "平台不能为空")
    private GeoLocationProvider provider;
    // Configuration
    private GeoLocationConfig config;

    public GeoLocationProviderConfig() {
    }

    public GeoLocationProviderConfig(GeoLocationProvider provider, GeoLocationConfig config) {
        this.provider = provider;
        this.config = config;
    }

    @Data
    public static class GeoLocationConfig implements Serializable {
        @Serial
        private static final long serialVersionUID = 5611656522133230183L;
    }

}
