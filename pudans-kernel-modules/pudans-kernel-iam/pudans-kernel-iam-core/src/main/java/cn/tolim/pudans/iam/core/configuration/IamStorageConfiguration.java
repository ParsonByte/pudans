package cn.tolim.pudans.iam.core.configuration;

import cn.tolim.pudans.contract.repository.client.FileRepositoryClient;
import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.core.storage.RepositoryStorage;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IamStorageConfiguration {

    private static final String INTERNAL_TOKEN_HEADER = "X-Pudans-Internal-Token";

    // IAM file storage is delegated to the platform file repository service
    // Parameter repositoryClient: FileRepositoryClient
    // Returns: Storage
    @Bean
    public Storage storage(FileRepositoryClient repositoryClient) {
        return new RepositoryStorage(repositoryClient);
    }

    // Adds the shared internal token to service-to-service Feign calls
    // Parameter internalToken: String
    // Returns: RequestInterceptor
    // Rebuilt after a Nacos config refresh so rotated internal tokens are picked up
    @Bean
    @RefreshScope
    public RequestInterceptor internalTokenRequestInterceptor(
            @Value("${pudans.internal.token}") String internalToken) {
        return template -> template.header(INTERNAL_TOKEN_HEADER, internalToken);
    }
}