package cn.tolim.pudans.core.storage.driver.cos;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import cn.tolim.pudans.infra.properties.PudansProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.endpoint.UserSpecifiedEndpointBuilder;
import com.qcloud.cos.region.Region;

@Component
@RequiredArgsConstructor
public class CosStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        return List.of(new StoragePolicyTypeDescriptor(
                StoragePolicyType.COS,
                "COS",
                "腾讯云对象存储 COS。",
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
        var settings = spec.getSettings();
        var region = settings != null && StringUtils.hasText(settings.getRegion())
                ? settings.getRegion()
                : "ap-guangzhou";
        var configuration = new ClientConfig(new Region(region));
        if (StringUtils.hasText(spec.getServer())) {
            var endpoint = spec.getServer().replaceFirst("^https?://", "");
            configuration.setEndpointBuilder(new UserSpecifiedEndpointBuilder(endpoint, endpoint));
        }
        var client = new COSClient(
                new BasicCOSCredentials(
                        resolved.credentials().getAccessKey(), resolved.credentials().getSecretKey()),
                configuration);
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new CosStorageDriver(policy, client, tempDirectory);
    }
}
