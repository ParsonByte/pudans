package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactoryRegistry;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import cn.tolim.pudans.core.storage.driver.local.LocalStorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.upyun.UpyunStorageDriverFactory;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;

class StoragePolicyValidatorTest {

    @TempDir
    Path workDir;

    private StoragePolicyValidator validator;

    @BeforeEach
    void setUp() {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        var registry = new StorageDriverFactoryRegistry(
                List.of(new LocalStorageDriverFactory(properties), new TestS3Factory(), new UpyunStorageDriverFactory(properties)));
        validator = new StoragePolicyValidator(registry, new StoragePolicyPathGenerator());
    }

    @Test
    void acceptsAValidLocalPolicy() {
        var result = validator.validatePolicy(localPolicy());

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void rejectsUnsafePathRules() {
        var policy = localPolicy();
        policy.getSpec().setDirNameRule("uploads/../{path}");

        var result = validator.validatePolicy(policy);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("上级目录"));
    }

    @Test
    void requiresBucketAndCredentialsForS3() {
        var policy = s3Policy();
        policy.getSpec().setBucketName(null);
        policy.getSpec().getCredentials().setAccessKey(null);
        policy.getSpec().getCredentials().setSecretKey(null);

        var result = validator.validatePolicy(policy);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("存储桶"));
        assertThat(result.errors()).anyMatch(error -> error.contains("Access Key"));
    }

    @Test
    void acceptsAValidS3Policy() {
        var result = validator.validatePolicy(s3Policy());

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejectsAnS3ChunkSizeBelowTheMinimum() {
        var policy = s3Policy();
        policy.getSpec().setChunkSize(1024L * 1024L);

        var result = validator.validatePolicy(policy);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("分片大小"));
    }

    @Test
    void enforcesTheFileTypeAllowList() {
        var policy = localPolicy();
        policy.getSpec().setFileTypes(List.of("png", "jpg"));

        validator.validateUpload(policy, "photo.png", 10);

        assertThatThrownBy(() -> validator.validateUpload(policy, "payload.exe", 10))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-file-type-not-allowed"));
    }

    @Test
    void enforcesTheFileTypeDenyList() {
        var policy = localPolicy();
        policy.getSpec().setFileTypes(List.of("exe"));
        policy.getSpec().setFileTypeDenyList(true);

        validator.validateUpload(policy, "photo.png", 10);
        assertThatThrownBy(() -> validator.validateUpload(policy, "payload.exe", 10))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void enforcesTheNameRegexp() {
        var policy = localPolicy();
        policy.getSpec().setNameRegexp(".*\\.pdf");

        validator.validateUpload(policy, "report.pdf", 10);
        assertThatThrownBy(() -> validator.validateUpload(policy, "report.txt", 10))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-file-name-not-allowed"));
    }

    @Test
    void enforcesTheNameRegexpDenyList() {
        var policy = localPolicy();
        policy.getSpec().setNameRegexp(".*\\.pdf");
        policy.getSpec().setNameRegexpDenyList(true);

        validator.validateUpload(policy, "report.txt", 10);
        assertThatThrownBy(() -> validator.validateUpload(policy, "report.pdf", 10))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void enforcesTheFileSizeLimit() {
        var policy = localPolicy();
        policy.getSpec().setMaxSize(10);

        assertThatThrownBy(() -> validator.validateUpload(policy, "a.png", 11))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-file-too-large"));
    }

    @Test
    void computesPartCountAndChunkSize() {
        var policy = s3Policy();
        policy.getSpec().setChunkSize(5L * 1024L * 1024L);

        assertThat(validator.chunkSize(policy)).isEqualTo(5L * 1024L * 1024L);
        assertThat(validator.partCount(11L * 1024L * 1024L, 5L * 1024L * 1024L)).isEqualTo(3);
    }

    @Test
    void acceptsAValidUpyunPolicy() {
        var result = validator.validatePolicy(upyunPolicy());

        assertThat(result.valid()).isTrue();
    }

    @Test
    void requiresUpyunOperatorCredentials() {
        var policy = upyunPolicy();
        policy.getSpec().getCredentials().setAccessKey(null);
        policy.getSpec().getCredentials().setSecretKey(null);
        policy.getSpec().getCredentials().setToken("token");

        var result = validator.validatePolicy(policy);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("操作员"));
        assertThat(result.errors()).anyMatch(error -> error.contains("密码"));
    }

    private static StoragePolicy upyunPolicy() {
        var policy = new StoragePolicy();
        var spec = policy.getSpec();
        spec.setDisplayName("upyun");
        spec.setType(StoragePolicyType.UPYUN);
        spec.setServer("https://v0.api.upyun.com");
        spec.setBucketName("bucket");
        spec.setDirNameRule("uploads/{path}");
        spec.setFileNameRule("{randomkey8}_{originname}");
        spec.getCredentials().setAccessKey("operator");
        spec.getCredentials().setSecretKey("password");
        return policy;
    }

    private static StoragePolicy localPolicy() {
        var policy = new StoragePolicy();
        var spec = policy.getSpec();
        spec.setDisplayName("local");
        spec.setType(StoragePolicyType.LOCAL);
        spec.setDirNameRule("uploads/{path}");
        spec.setFileNameRule("{randomkey8}_{originname}");
        return policy;
    }

    private static StoragePolicy s3Policy() {
        var policy = new StoragePolicy();
        var spec = policy.getSpec();
        spec.setDisplayName("s3");
        spec.setType(StoragePolicyType.S3);
        spec.setServer("https://s3.example.com");
        spec.setBucketName("bucket");
        spec.setDirNameRule("uploads/{path}");
        spec.setFileNameRule("{randomkey8}_{originname}");
        spec.getCredentials().setAccessKey("ak");
        spec.getCredentials().setSecretKey("sk");
        return policy;
    }

    private static final class TestS3Factory implements StorageDriverFactory {

        @Override
        public List<StoragePolicyTypeDescriptor> descriptors() {
            return List.of(new StoragePolicyTypeDescriptor(
                    StoragePolicyType.S3, "S3", "S3 compatible", true, Set.of(StorageCapability.MULTIPART_UPLOAD)));
        }

        @Override
        public StorageDriver create(ResolvedStoragePolicy policy) {
            throw new UnsupportedOperationException();
        }
    }
}
