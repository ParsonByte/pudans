package cn.tolim.pudans.infra.r2dbc;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
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
}
