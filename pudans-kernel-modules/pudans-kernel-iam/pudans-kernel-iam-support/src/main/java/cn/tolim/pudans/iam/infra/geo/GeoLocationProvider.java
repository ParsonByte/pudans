package cn.tolim.pudans.iam.infra.geo;

import java.io.Serializable;
import java.util.Objects;

public class GeoLocationProvider implements Serializable {
    public static final GeoLocationProvider NONE = new GeoLocationProvider("none", "None");

    private String provider;
    private String name;

    public GeoLocationProvider() {
    }

    public GeoLocationProvider(String provider, String name) {
        this.provider = provider;
        this.name = name;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GeoLocationProvider value
                && Objects.equals(provider, value.provider) && Objects.equals(name, value.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(provider, name);
    }
}
