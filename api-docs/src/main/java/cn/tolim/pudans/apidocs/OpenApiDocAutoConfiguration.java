package cn.tolim.pudans.apidocs;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;

// Registers the springdoc group fetched by the OpenAPI document generation plugin.
// The group name comes from application-doc.yml and becomes the generated file name.
@AutoConfiguration
@Profile("doc")
@ConditionalOnClass(GroupedOpenApi.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class OpenApiDocAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI pudansOpenApiMetadata(
            @Value("${spring.application.name:pudans}") String applicationName,
            @Value("${pudans.api-docs.version:v0}") String version) {
        return new OpenAPI().info(new Info().title(applicationName).version(version));
    }

    @Bean
    public GroupedOpenApi pudansServiceApiDocs(
            @Value("${pudans.api-docs.group:apis_pudans.v1alpha1}") String group,
            @Value("${pudans.api-docs.paths:/**}") String[] paths) {
        return GroupedOpenApi.builder()
                .group(group)
                .pathsToMatch(paths)
                .build();
    }

}