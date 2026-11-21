package cn.tolim.pudans.core.storage.driver.oss;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import cn.tolim.pudans.infra.properties.PudansProperties;
import com.aliyun.oss.OSSClientBuilder;

@Component
@RequiredArgsConstructor
public class OssStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.OSS,
                "OSS",
                "阿里云对象存储 OSS。",
                true,
                Set.of(
                        StorageCapability.PRESIGNED_SOURCE,
                        StorageCapability.MULTIPART_UPLOAD,
                        StorageCapability.RANGE_READ,
                        StorageCapability.CORS_CONFIGURABLE)));
    }

    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        var policy = resolved.policy();
        var spec = policy.getSpec();
        var client = new OSSClientBuilder().build(
                spec.getServer(), resolved.credentials().getAccessKey(), resolved.credentials().getSecretKey());
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new OssStorageDriver(policy, client, tempDirectory);
    }
}
