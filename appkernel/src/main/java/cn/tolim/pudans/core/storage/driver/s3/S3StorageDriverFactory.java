package cn.tolim.pudans.core.storage.driver.s3;

import java.net.URI;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.StoragePolicyTypeDescriptor;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Component
public class S3StorageDriverFactory implements StorageDriverFactory {

    @Override
    public List<StoragePolicyTypeDescriptor> descriptors() {
        var capabilities = Set.of(
                StorageCapability.PRESIGNED_SOURCE,
                StorageCapability.PRESIGNED_PARTS,
                StorageCapability.MULTIPART_UPLOAD,
                StorageCapability.RANGE_READ,
                StorageCapability.CORS_CONFIGURABLE);
        return List.of(
                new StoragePolicyTypeDescriptor(
                        StoragePolicyType.S3, "S3", "AWS S3 或兼容 S3 协议的对象存储。", true, capabilities),
                new StoragePolicyTypeDescriptor(
                        StoragePolicyType.KS3, "KS3", "金山云 KS3（兼容 S3 协议）。", true, capabilities));
    }

    @Override
    public StorageDriver create(ResolvedStoragePolicy resolved) {
        var policy = resolved.policy();
        var spec = policy.getSpec();
        var settings = spec.getSettings();
        var region = settings != null && StringUtils.hasText(settings.getRegion())
                ? settings.getRegion()
                : "us-east-1";
        var pathStyle = (settings != null && settings.isS3PathStyle())
                || spec.getType() == StoragePolicyType.KS3;
        var credentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(
                resolved.credentials().getAccessKey(), resolved.credentials().getSecretKey()));
        var clientBuilder = S3AsyncClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .httpClientBuilder(NettyNioAsyncHttpClient.builder());
        var presignerBuilder = S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentials)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build());
        if (StringUtils.hasText(spec.getServer())) {
            var endpoint = URI.create(spec.getServer());
            clientBuilder.endpointOverride(endpoint);
            presignerBuilder.endpointOverride(endpoint);
        }
        if (pathStyle) {
            clientBuilder.forcePathStyle(true);
        }
        return new S3StorageDriver(policy, clientBuilder.build(), presignerBuilder.build());
    }
}
