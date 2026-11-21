package cn.tolim.pudans.core.storage.driver.upyun;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import cn.tolim.pudans.core.infra.properties.PudansProperties;
import com.UpYun;

@Component
@RequiredArgsConstructor
public class UpyunStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.UPYUN,
                "又拍云",
                "又拍云对象存储。",
                true,
                Set.of(StorageCapability.DIRECT_UPLOAD, StorageCapability.RANGE_READ)));
    }

    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        var policy = resolved.policy();
        var client = new UpYun(
                policy.getSpec().getBucketName(),
                resolved.credentials().getAccessKey(),
                resolved.credentials().getSecretKey());
        if (StringUtils.hasText(policy.getSpec().getServer())) {
            client.setApiDomain(policy.getSpec().getServer().replaceFirst("^https?://", ""));
        }
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        try {
            Files.createDirectories(tempDirectory);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("无法创建存储临时目录", e);
        }
        return new UpyunStorageDriver(
                policy,
                client,
                resolved.credentials().getAccessKey(),
                resolved.credentials().getSecretKey(),
                tempDirectory);
    }
}
