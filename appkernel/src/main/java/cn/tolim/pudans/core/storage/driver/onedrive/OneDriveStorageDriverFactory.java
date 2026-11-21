package cn.tolim.pudans.core.storage.driver.onedrive;

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

@Component
@RequiredArgsConstructor
public class OneDriveStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    // 声明 OneDrive 类型及其支持的能力。
    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.ONEDRIVE,
                "OneDrive",
                "Microsoft OneDrive（Graph API）。",
                true,
                Set.of(StorageCapability.PRESIGNED_SOURCE, StorageCapability.RANGE_READ)));
    }

    // 以工作目录临时区创建 OneDrive 驱动。
    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new OneDriveStorageDriver(resolved.policy(), resolved.credentials(), tempDirectory);
    }
}
