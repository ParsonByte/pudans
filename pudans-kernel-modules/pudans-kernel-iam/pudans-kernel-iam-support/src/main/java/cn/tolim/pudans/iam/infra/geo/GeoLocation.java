package cn.tolim.pudans.iam.infra.geo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

import java.io.Serializable;
import java.util.Objects;

public class GeoLocation implements Serializable {
    private String ip;
    private String continentCode;
    private String continentName;
    private String countryCode;
    private String countryName;
    private String provinceCode;
    private String provinceName;
    private String cityCode;
    private String cityName;
    private Double latitude;
    private Double longitude;
    private GeoLocationProvider provider;

    public GeoLocation() {
    }

    public GeoLocation(String ip, String continentCode, String continentName, String countryCode,
                       String countryName, String provinceCode, String provinceName, String cityCode,
                       String cityName, Double latitude, Double longitude,
                       GeoLocationProvider provider) {
        this.ip = ip;
        this.continentCode = continentCode;
        this.continentName = continentName;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.provinceCode = provinceCode;
        this.provinceName = provinceName;
        this.cityCode = cityCode;
        this.cityName = cityName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.provider = provider;
    }

    public static GeoLocationBuilder builder() {
        return new GeoLocationBuilder();
    }

    @JsonProperty("ip")
    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    @JsonProperty("continentCode")
    public String getContinentCode() {
        return continentCode;
    }

    public void setContinentCode(String continentCode) {
        this.continentCode = continentCode;
    }

    @JsonProperty("continentName")
    public String getContinentName() {
        return continentName;
    }

    public void setContinentName(String continentName) {
        this.continentName = continentName;
    }

    @JsonProperty("countryCode")
    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    @JsonProperty("countryName")
    public String getCountryName() {
        return countryName;
    }

    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }

    @JsonProperty("provinceCode")
    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    @JsonProperty("provinceName")
    public String getProvinceName() {
        return provinceName;
    }

    public void setProvinceName(String provinceName) {
        this.provinceName = provinceName;
    }

    @JsonProperty("cityCode")
    public String getCityCode() {
        return cityCode;
    }

    public void setCityCode(String cityCode) {
        this.cityCode = cityCode;
    }

    @JsonProperty("cityName")
    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    @JsonProperty("latitude")
    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    @JsonProperty("longitude")
    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    @JsonProperty("provider")
    public GeoLocationProvider getProvider() {
        return provider;
    }

    public void setProvider(GeoLocationProvider provider) {
        this.provider = provider;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeoLocation location)) {
            return false;
        }
        return Objects.equals(ip, location.ip) && Objects.equals(countryCode, location.countryCode)
                && Objects.equals(cityCode, location.cityCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, countryCode, cityCode);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GeoLocationBuilder {
        private String ip;
        private String continentCode;
        private String continentName;
        private String countryCode;
        private String countryName;
        private String provinceCode;
        private String provinceName;
        private String cityCode;
        private String cityName;
        private Double latitude;
        private Double longitude;
        private GeoLocationProvider provider;

        public GeoLocationBuilder ip(String value) {
            this.ip = value;
            return this;
        }

        public GeoLocationBuilder continentCode(String value) {
            this.continentCode = value;
            return this;
        }

        public GeoLocationBuilder continentName(String value) {
            this.continentName = value;
            return this;
        }

        public GeoLocationBuilder countryCode(String value) {
            this.countryCode = value;
            return this;
        }

        public GeoLocationBuilder countryName(String value) {
            this.countryName = value;
            return this;
        }

        public GeoLocationBuilder provinceCode(String value) {
            this.provinceCode = value;
            return this;
        }

        public GeoLocationBuilder provinceName(String value) {
            this.provinceName = value;
            return this;
        }

        public GeoLocationBuilder cityCode(String value) {
            this.cityCode = value;
            return this;
        }

        public GeoLocationBuilder cityName(String value) {
            this.cityName = value;
            return this;
        }

        public GeoLocationBuilder latitude(Double value) {
            this.latitude = value;
            return this;
        }

        public GeoLocationBuilder longitude(Double value) {
            this.longitude = value;
            return this;
        }

        public GeoLocationBuilder provider(GeoLocationProvider value) {
            this.provider = value;
            return this;
        }

        public GeoLocation build() {
            return new GeoLocation(ip, continentCode, continentName, countryCode, countryName,
                    provinceCode, provinceName, cityCode, cityName, latitude, longitude, provider);
        }
    }
}
