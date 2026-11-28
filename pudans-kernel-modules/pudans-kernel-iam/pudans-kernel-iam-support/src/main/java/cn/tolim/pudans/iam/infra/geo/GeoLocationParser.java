package cn.tolim.pudans.iam.infra.geo;

public interface GeoLocationParser {
    GeoLocation getGeoLocation(String ip);
}
