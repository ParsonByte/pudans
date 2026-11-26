package cn.tolim.pudans.core.storage.driver.cos;

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
import cn.tolim.pudans.infra.properties.PudansProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.endpoint.UserSpecifiedEndpointBuilder;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;

@Component
@RequiredArgsConstructor
public class CosStorageDriverFactory implements StorageDriverFactory {

    private final PudansProperties properties;

    // 声明 COS 类型及其支持的能力。
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

    // 按策略区域与端点创建 COS 客户端，并禁止服务端重定向。
    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        var policy = resolved.policy();
        var spec = policy.getSpec();
        var settings = spec.getSettings();
        var region = settings != null && StringUtils.hasText(settings.getRegion())
                ? settings.getRegion()
                : "ap-guangzhou";
        var configuration = new ClientConfig(new Region(region));
        configuration.setRedirectsEnabled(false);
        if (StringUtils.hasText(spec.getServer())) {
            var server = spec.getServer();
            var endpoint = server.replaceFirst("^https?://", "");
            // COS SDK 的自定义端点不会自动拼接存储桶域名，这里按 <bucket>.<endpoint> 组装。
            var bucket = spec.getBucketName();
            var host = StringUtils.hasText(bucket) && !endpoint.startsWith(bucket + ".")
                    ? bucket + "." + endpoint
                    : endpoint;
            configuration.setEndpointBuilder(new UserSpecifiedEndpointBuilder(host, host));
            // 端点声明 http 时同步切换客户端协议，否则 SDK 默认仍按 https 请求。
            if (server.startsWith("http://")) {
                configuration.setHttpProtocol(HttpProtocol.http);
            }
        }
        var client = new COSClient(
                new BasicCOSCredentials(
                        resolved.credentials().getAccessKey(), resolved.credentials().getSecretKey()),
                configuration);
        Path tempDirectory = properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
        return new CosStorageDriver(policy, client, tempDirectory);
    }
}
