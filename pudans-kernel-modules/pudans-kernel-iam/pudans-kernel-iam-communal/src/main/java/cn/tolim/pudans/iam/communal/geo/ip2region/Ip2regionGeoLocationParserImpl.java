package cn.tolim.pudans.iam.communal.geo.ip2region;

import cn.tolim.pudans.iam.communal.enums.Country;
import cn.tolim.pudans.iam.infra.geo.GeoLocation;
import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import cn.tolim.pudans.iam.infra.geo.GeoLocationProvider;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.geo.District.CITY_DISTRICT;
import static cn.tolim.pudans.iam.communal.geo.District.PROVINCE_DISTRICT;


@Slf4j
public class Ip2regionGeoLocationParserImpl implements GeoLocationParser, DisposableBean {

    private static final String DEFAULT_XDB_PATH = "/ip2region/ip2region.xdb";
    public static GeoLocationProvider IP2REGION = new GeoLocationProvider("default",
            "ip2region");
    private final Searcher searcher;

    public Ip2regionGeoLocationParserImpl() throws IOException {
        // Load the entire xdb into memory.
        try (InputStream in = this.getClass().getResourceAsStream(DEFAULT_XDB_PATH)) {
            Assert.notNull(in, "XDB 不能为空");
            log.info("创建内容缓存搜索器");
            // 2. Use cBuff to create an in-memory query object.
            searcher = Searcher.newWithBuffer(Version.IPv4, Searcher.loadContentFromInputStream(in));
        }
    }

    @Override
    public GeoLocation getGeoLocation(String remote) {
        try {
            String region = searcher.search(remote);
            return buildGeoLocation(remote, region);
        } catch (Exception e) {
            log.error("搜索失败({})", remote, e);
        }
        return GeoLocation.builder().provider(IP2REGION).ip(remote).build();
    }

    private GeoLocation buildGeoLocation(String remote, String region) {
        String[] geo = region.split("\\|");
        String countryName = geo[0];
        String provinceName = geo[2];
        String cityName = geo[3];
        Country country = Country.getByCountryName(countryName);
        return GeoLocation.builder().provider(IP2REGION).ip(remote)
                .countryCode(Objects.nonNull(country) ? country.getCode() : null)
                .countryName(countryName)
                .provinceCode(Objects.toString(PROVINCE_DISTRICT.get(provinceName)))
                .provinceName(provinceName).cityCode(Objects.toString(CITY_DISTRICT.get(cityName)))
                .cityName(cityName).build();
    }

    @Override
    public void destroy() throws Exception {
        if (Objects.nonNull(searcher)) {
            searcher.close();
        }
    }
}
