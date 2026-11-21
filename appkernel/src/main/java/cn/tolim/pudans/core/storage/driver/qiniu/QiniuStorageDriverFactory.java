package cn.tolim.pudans.core.storage.driver.qiniu;

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
import com.qiniu.util.Auth;

@Component
@RequiredArgsConstructor
public class QiniuStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.QINIU,
                "七牛云",
                "七牛云对象存储 Kodo。",
                true,
                Set.of(
                        StorageCapability.PRESIGNED_SOURCE,
                        StorageCapability.DIRECT_UPLOAD,
                        StorageCapability.RANGE_READ)));
    }

    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        var policy = resolved.policy();
        var auth = Auth.create(
                resolved.credentials().getAccessKey(), resolved.credentials().getSecretKey());
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new QiniuStorageDriver(policy, auth, tempDirectory);
    }
}
