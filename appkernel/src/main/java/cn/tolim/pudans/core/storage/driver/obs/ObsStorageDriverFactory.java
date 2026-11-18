package cn.tolim.pudans.core.storage.driver.obs;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import cn.tolim.pudans.infra.properties.PudansProperties;
import com.obs.services.ObsClient;

@Component
@RequiredArgsConstructor
public class ObsStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.OBS,
                "OBS",
                "华为云对象存储 OBS。",
                true,
                Set.of(
                        StorageCapability.MULTIPART_UPLOAD,
                        StorageCapability.RANGE_READ,
                        StorageCapability.CORS_CONFIGURABLE)));
    }

    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        var policy = resolved.policy();
        var client = new ObsClient(
                resolved.credentials().getAccessKey(),
                resolved.credentials().getSecretKey(),
                policy.getSpec().getServer());
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new ObsStorageDriver(policy, client, tempDirectory);
    }
}
