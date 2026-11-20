package cn.tolim.pudans.infra.r2dbc;

import cn.tolim.pudans.infra.utils.PudansTime;
import io.r2dbc.spi.Option;
import java.util.TimeZone;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.mapping.R2dbcMappingContext;

@Configuration(proxyBeanMethods = false)
class R2dbcConfiguration {


    @Bean
    static BeanPostProcessor r2dbcMappingContextQuoteModifier() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof R2dbcMappingContext mappingContext) {
                    mappingContext.setForceQuote(false);
                }
                return bean;
            }
        };
    }

    // 对应 PostgreSQL R2DBC 驱动的 TIME_ZONE 连接选项，连接建立时会话时区固定为北京时间
    static final Option<TimeZone> POSTGRES_TIME_ZONE = Option.valueOf("timeZone");

    // 会话时区固定为北京时间，业务日期函数不依赖数据库服务器默认时区
    @Bean
    static ConnectionFactoryOptionsBuilderCustomizer beijingSessionTimeZone() {
        return builder ->
                builder.option(POSTGRES_TIME_ZONE, TimeZone.getTimeZone(PudansTime.BEIJING));
    }
}