package cn.tolim.pudans.infra.config;

import static org.springdoc.core.utils.Constants.SPRINGDOC_ENABLED;

import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.extension.router.JsonPatch;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.jackson.TypeNameResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Set;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnProperty(name = SPRINGDOC_ENABLED, matchIfMissing = true)
@ConditionalOnWebApplication
public class SwaggerConfig {

    @Bean
    OpenAPI pudansOpenApi(
            ObjectProvider<BuildProperties> buildPropertiesProvider, SpringDocConfigProperties docConfigProperties) {
        var buildProperties = buildPropertiesProvider.getIfAvailable();
        var version = "unknown";
        if (buildProperties != null) {
            version = buildProperties.getVersion();
        }
        return new OpenAPI()
                .specVersion(docConfigProperties.getSpecVersion())

                .components(new Components()
                        .addSecuritySchemes(
                                "basicAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("basic"))
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("basicAuth").addList("bearerAuth"))
                .info(new Info().title("pudans").version(version));
    }

    @Bean
    GlobalOpenApiCustomizer openApiCustomizer() {
        return openApi -> JsonPatch.addSchema(openApi.getComponents());
    }

    @Bean
    GroupedOpenApi aggregatedV1alpha1Api() {
        return GroupedOpenApi.builder()
                .group("apis_aggregated.api_v1alpha1")
                .displayName("Aggregated API V1alpha1")
                .pathsToMatch(
                        "/apis/" + ApiGroups.CONSOLE + "/**",
                        "/apis/" + ApiGroups.UC + "/**",
                        "/apis/" + ApiGroups.PUBLIC + "/**",
                        "/apis/" + ApiGroups.EXTENSION + "/**",
                        "/apis/" + ApiGroups.AUTH + "/**",
                        "/login/**")
                .build();
    }

    @Bean
    GroupedOpenApi publicV1alpha1Api() {
        return GroupedOpenApi.builder()
                .group("apis_public.api_v1alpha1")
                .displayName("Public API V1alpha1")
                .pathsToMatch("/apis/" + ApiGroups.PUBLIC + "/**")
                .build();
    }

    @Bean
    GroupedOpenApi consoleV1alpha1Api() {
        return GroupedOpenApi.builder()
                .group("apis_console.api_v1alpha1")
                .displayName("Console API V1alpha1")
                .pathsToMatch("/apis/" + ApiGroups.CONSOLE + "/**")
                .build();
    }

    @Bean
    GroupedOpenApi ucV1alpha1Api() {
        return GroupedOpenApi.builder()
                .group("apis_uc.api_v1alpha1")
                .displayName("User-center API V1alpha1")
                .pathsToMatch("/apis/" + ApiGroups.UC + "/**")
                .build();
    }

    @Bean
    GroupedOpenApi authV1alpha1Api() {
        return GroupedOpenApi.builder()
                .group("apis_auth.api_v1alpha1")
                .displayName("Auth API V1alpha1")
                .pathsToMatch("/apis/" + ApiGroups.AUTH + "/**")
                .build();
    }

    @Bean
    GroupedOpenApi extensionV1alpha1Api() {
        return GroupedOpenApi.builder()
                .group("apis_extension.api_v1alpha1")
                .displayName("Extension API V1alpha1")
                .pathsToMatch("/apis/" + ApiGroups.EXTENSION + "/**")
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    ModelConverter customModelConverter(ObjectMapperProvider objectMapperProvider) {
        return new ModelResolver(objectMapperProvider.jsonMapper(), new CustomTypeNameResolver());
    }

    static class CustomTypeNameResolver extends TypeNameResolver {
        @Override
        protected String nameForClass(Class<?> cls, Set<Options> options) {


            return super.nameForClass(cls, options).replaceAll("\\$", ".");
        }
    }
}
