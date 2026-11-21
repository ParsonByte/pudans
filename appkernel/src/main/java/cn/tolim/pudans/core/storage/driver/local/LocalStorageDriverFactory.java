package cn.tolim.pudans.core.storage.driver.local;

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
import cn.tolim.pudans.core.infra.properties.PudansProperties;

@Component
@RequiredArgsConstructor
public class LocalStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.LOCAL,
                "本地存储",
                "使用服务端工作目录保存文件。",
                false,
                Set.of(StorageCapability.RANGE_READ, StorageCapability.PROXY_REQUIRED)));
    }

    @Override
    public StorageDriver create(ResolvedStoragePolicy policy) {
        return new LocalStorageDriver(policy.policy(), properties.getWorkDir().toAbsolutePath().normalize());
    }
}
