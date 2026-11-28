package cn.tolim.pudans.iam.communal.geo;

import cn.tolim.pudans.iam.infra.geo.GeoLocation;
import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import cn.tolim.pudans.iam.infra.geo.GeoLocationProvider;


public class NoneGeoLocationParserImpl implements GeoLocationParser {

    // Get location
    // Parameter remote: String
    // Returns: GeoLocation
    @Override
    public GeoLocation getGeoLocation(String remote) {
        return GeoLocation.builder().ip(remote).provider(GeoLocationProvider.NONE).build();
    }
}
