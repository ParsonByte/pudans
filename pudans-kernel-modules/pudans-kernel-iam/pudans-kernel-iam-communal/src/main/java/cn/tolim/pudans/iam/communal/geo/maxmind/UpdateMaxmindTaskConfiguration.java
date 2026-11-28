package cn.tolim.pudans.iam.communal.geo.maxmind;

import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class UpdateMaxmindTaskConfiguration {
    private final Logger logger = LoggerFactory.getLogger(UpdateMaxmindTaskConfiguration.class);
    private final GeoLocationParser geoLocationParser;

    // Update the IP database every day at 01:00
    @Scheduled(cron = "0 0 1 * * ?")
    public void update() {
        try {
            if (geoLocationParser instanceof MaxmindGeoLocationParserImpl maxmindGeoLocation) {
                logger.info("执行IP库文件更新定时任务开始");
                if (maxmindGeoLocation.checkDbFileIsUpdate()) {
                    maxmindGeoLocation.download();
                }
                logger.info("执行IP库文件更新定时任务结束");
            }
        } catch (Exception e) {
            logger.error("执行IP库文件更新定时发生异常：{}", e.getMessage());
        }
    }

}
