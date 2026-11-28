package cn.tolim.pudans.repository.config;

import cn.tolim.pudans.repository.security.PlaintextSecretCipher;
import cn.tolim.pudans.repository.security.SecretCipher;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RepositoryAutoConfigurationTests {

    @TempDir
    Path tempDir;

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RepositoryAutoConfiguration.class));

    @Test
    void bindsPropertiesAndCreatesLocalRegistry() {
        runner.withPropertyValues(
                "pudans.repository.default-provider=local",
                "pudans.repository.default-tenant-id=binding-tenant",
                "pudans.repository.max-file-size=2048",
                "pudans.repository.local.base-directory=" + tempDir.resolve("binding"),
                "pudans.repository.aliyun-oss.enabled=false"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            RepositoryProperties properties = context.getBean(RepositoryProperties.class);
            assertThat(properties.getDefaultProvider()).isEqualTo(StorageProviderType.LOCAL);
            assertThat(properties.getDefaultTenantId()).isEqualTo("binding-tenant");
            assertThat(properties.getMaxFileSize()).isEqualTo(2048);

            ObjectStorageRegistry registry = context.getBean(ObjectStorageRegistry.class);
            assertThat(registry.defaultProvider()).isEqualTo(StorageProviderType.LOCAL);
            assertThat(registry.isConfigured(StorageProviderType.LOCAL)).isTrue();
            assertThat(registry.isConfigured(StorageProviderType.S3)).isFalse();

            assertThat(context.getBean(SecretCipher.class))
                    .isInstanceOf(PlaintextSecretCipher.class);
        });
    }

    @Test
    void canSelectCloudProviderAsDefault() {
        runner.withPropertyValues(
                "pudans.repository.default-provider=s3",
                "pudans.repository.local.enabled=false",
                "pudans.repository.s3.enabled=true",
                "pudans.repository.s3.endpoint=http://127.0.0.1:9000",
                "pudans.repository.s3.region=us-east-1",
                "pudans.repository.s3.bucket=test-bucket",
                "pudans.repository.s3.access-key-id=test-key",
                "pudans.repository.s3.secret-access-key=test-secret"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            ObjectStorageRegistry registry = context.getBean(ObjectStorageRegistry.class);
            assertThat(registry.defaultProvider()).isEqualTo(StorageProviderType.S3);
            assertThat(registry.isConfigured(StorageProviderType.S3)).isTrue();
        });
    }

    @Test
    void failsWhenDefaultProviderIsNotConfigured() {
        runner.withPropertyValues(
                "pudans.repository.default-provider=s3",
                "pudans.repository.local.enabled=true"
        ).run(context -> assertThat(context).hasFailed());
    }

    @Test
    void moduleCanBeDisabled() {
        runner.withPropertyValues("pudans.repository.enabled=false")
                .run(context -> assertThat(context)
                        .doesNotHaveBean(ObjectStorageRegistry.class));
    }
}