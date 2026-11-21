package cn.tolim.pudans.core.storage.driver.obs;

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
import cn.tolim.pudans.core.storage.driver.ValidatingOkHttpDns;
import cn.tolim.pudans.infra.properties.PudansProperties;
import com.obs.services.ObsClient;
import com.obs.services.ObsConfiguration;

@Component
@RequiredArgsConstructor
public class ObsStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    // 声明 OBS 类型及其支持的能力。
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

    // 按策略端点创建 OBS 客户端。
    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        var policy = resolved.policy();
        var configuration = new ObsConfiguration();
        configuration.setEndPoint(policy.getSpec().getServer());
        configuration.setCustomizedDnsImpl(new ValidatingOkHttpDns());
        var client = new ObsClient(
                resolved.credentials().getAccessKey(),
                resolved.credentials().getSecretKey(),
                configuration);
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new ObsStorageDriver(policy, client, tempDirectory);
    }
}
