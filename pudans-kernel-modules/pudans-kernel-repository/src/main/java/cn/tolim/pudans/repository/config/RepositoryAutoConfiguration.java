package cn.tolim.pudans.repository.config;

import cn.tolim.pudans.repository.security.PlaintextSecretCipher;
import cn.tolim.pudans.repository.security.SecretCipher;
import cn.tolim.pudans.repository.security.SpissatusSecretCipher;
import cn.tolim.pudans.repository.service.ChecksumService;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import cn.tolim.pudans.repository.storage.StorageProviderFactory;
import cn.tolim.spissatus4j.contract.api.Spissatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "pudans.repository", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(RepositoryProperties.class)
public class RepositoryAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SecretCipher.class)
    public SecretCipher repositorySecretCipher(ObjectProvider<Spissatus> spissatusProvider,
                                               RepositoryProperties properties) {
        Spissatus spissatus = spissatusProvider.getIfAvailable();
        if (spissatus == null) {
            return new PlaintextSecretCipher();
        }
        return new SpissatusSecretCipher(spissatus, properties.getCrypto());
    }

    @Bean
    @ConditionalOnMissingBean(ChecksumService.class)
    public ChecksumService repositoryChecksumService(ObjectProvider<Spissatus> spissatusProvider,
                                                     RepositoryProperties properties) {
        return new ChecksumService(spissatusProvider.getIfAvailable(), properties);
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStorageRegistry.class)
    public ObjectStorageRegistry objectStorageRegistry(RepositoryProperties properties,
                                                       SecretCipher secretCipher) {
        return StorageProviderFactory.create(properties, secretCipher);
    }
}