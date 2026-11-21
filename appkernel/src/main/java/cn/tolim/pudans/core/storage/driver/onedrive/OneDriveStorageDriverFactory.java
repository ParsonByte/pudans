package cn.tolim.pudans.core.storage.driver.onedrive;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.storage.resource.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import cn.tolim.pudans.infra.properties.PudansProperties;

@Component
@RequiredArgsConstructor
public class OneDriveStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.ONEDRIVE,
                "OneDrive",
                "Microsoft OneDrive（Graph API）。",
                true,
                Set.of(StorageCapability.PRESIGNED_SOURCE, StorageCapability.RANGE_READ)));
    }

    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new OneDriveStorageDriver(resolved.policy(), resolved.credentials(), tempDirectory);
    }
}
