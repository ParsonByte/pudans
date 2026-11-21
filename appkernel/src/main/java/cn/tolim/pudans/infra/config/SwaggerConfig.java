package cn.tolim.pudans.infra.config;

import static org.springdoc.core.utils.Constants.SPRINGDOC_ENABLED;

import cn.tolim.pudans.extension.contract.ApiGroups;
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
    // 汇总构建版本、安全方案与基础服务信息。
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
    // 将补丁请求模型注册到文档组件。
    GlobalOpenApiCustomizer openApiCustomizer() {
        return openApi -> JsonPatch.addSchema(openApi.getComponents());
    }

    @Bean
    // 按 API 根路径分组并排除系统配置端点。
    GroupedOpenApi apiV1alpha1Api() {
        return GroupedOpenApi.builder()
                .group("apis_api_v1alpha1")
                .displayName("Pudans API V1alpha1")
                .pathsToMatch(ApiGroups.API_ROOT + "/**", "/login/**")
                .pathsToExclude(ApiGroups.API_ROOT + "/systemconfigs/**")
                .build();
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    // 替换类型名解析以输出稳定的模型名。
    ModelConverter customModelConverter(ObjectMapperProvider objectMapperProvider) {
        return new ModelResolver(objectMapperProvider.jsonMapper(), new CustomTypeNameResolver());
    }

    static class CustomTypeNameResolver extends TypeNameResolver {
        // 将内部类分隔符替换为点号，保持文档类型名稳定。
        @Override
        protected String nameForClass(Class<?> cls, Set<Options> options) {


            return super.nameForClass(cls, options).replaceAll("\\$", ".");
        }
    }
}
