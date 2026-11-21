package cn.tolim.pudans.core.infra.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import tools.jackson.databind.MapperFeature;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@EnableCaching
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class PudansConfiguration {

    @Bean
    JsonMapperBuilderCustomizer objectMapperCustomizer(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return builder -> {
            builder.changeDefaultPropertyInclusion(
                    v -> v.withValueInclusion(NON_NULL).withContentInclusion(NON_NULL));
            builder.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
            builder.addModule(new JacksonAdapterModule(objectMapperProvider::getIfAvailable));
            SensitiveExtensionMixins.apply(builder);
        };
    }

}
