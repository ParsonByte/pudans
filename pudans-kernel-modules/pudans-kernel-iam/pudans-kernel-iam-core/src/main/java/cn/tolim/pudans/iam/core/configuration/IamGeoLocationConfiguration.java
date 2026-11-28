package cn.tolim.pudans.iam.core.configuration;

import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.geo.GeoLocationProviderConfig;
import cn.tolim.pudans.iam.communal.geo.NoneGeoLocationParserImpl;
import cn.tolim.pudans.iam.communal.geo.ip2region.Ip2regionGeoLocationParserImpl;
import cn.tolim.pudans.iam.communal.geo.maxmind.MaxmindGeoLocationParserImpl;
import cn.tolim.pudans.iam.communal.geo.maxmind.MaxmindProviderConfig;
import cn.tolim.pudans.iam.communal.jackjson.encrypt.EncryptionModule;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.core.setting.GeoIpProviderConstants;
import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.constant.ConfigBeanNameConstants.GEO_LOCATION;
import static cn.tolim.pudans.iam.communal.geo.ip2region.Ip2regionGeoLocationParserImpl.IP2REGION;
import static cn.tolim.pudans.iam.communal.geo.maxmind.MaxmindGeoLocationParserImpl.MAXMIND;


@Configuration
public class IamGeoLocationConfiguration {
    private final Logger logger = LoggerFactory.getLogger(IamGeoLocationConfiguration.class);

    @RefreshScope
    @Bean(value = GEO_LOCATION)
    public GeoLocationParser geoLocation(SettingRepository settingRepository,
                                         RestTemplate restTemplate) {
        try {
            ObjectMapper objectMapper = EncryptionModule.deserializerDecrypt();
            // Specify the serialized input type
            objectMapper.activateDefaultTyping(objectMapper.getPolymorphicTypeValidator(),
                    ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
            // Check whether geolocation is enabled in the database
            SettingEntity setting = settingRepository
                    .findByName(GeoIpProviderConstants.IPADDRESS_SETTING_NAME);
            if (!Objects.isNull(setting) && StringUtils.isNotBlank(setting.getValue())) {
                GeoLocationProviderConfig provider = objectMapper.readValue(setting.getValue(),
                        GeoLocationProviderConfig.class);
                // maxmind
                if (MAXMIND.equals(provider.getProvider())) {
                    return new MaxmindGeoLocationParserImpl(
                            (MaxmindProviderConfig) provider.getConfig(), restTemplate);
                }
                // ip2region
                if (IP2REGION.equals(provider.getProvider())) {
                    return new Ip2regionGeoLocationParserImpl();
                }
            }
            // Use ip2region by default when no data exists
            return new Ip2regionGeoLocationParserImpl();
        } catch (IOException e) {
            logger.error("创建地理位置服务异常：{}", e.getMessage(), e);
        }
        return new NoneGeoLocationParserImpl();
    }
}
