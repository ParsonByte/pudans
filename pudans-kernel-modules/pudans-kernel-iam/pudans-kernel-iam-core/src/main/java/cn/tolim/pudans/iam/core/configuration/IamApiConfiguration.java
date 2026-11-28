package cn.tolim.pudans.iam.core.configuration;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.CONTEXT_ENDPOINT;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.CONTEXT_ENDPOINT_GROUP_NAME;


@Configuration
public class IamApiConfiguration {

    // System context
    // Returns: GroupedOpenApi
    @Bean
    public GroupedOpenApi contextRestApi() {
        return GroupedOpenApi.builder().group(CONTEXT_ENDPOINT_GROUP_NAME)
                .pathsToMatch(CONTEXT_ENDPOINT + "/**").build();
    }

}
