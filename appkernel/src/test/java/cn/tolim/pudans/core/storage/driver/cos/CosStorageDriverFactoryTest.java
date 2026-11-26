package cn.tolim.pudans.core.storage.driver.cos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockConstruction;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ResolvedStoragePolicy;
import cn.tolim.pudans.infra.properties.PudansProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.http.HttpProtocol;

class CosStorageDriverFactoryTest {

    @TempDir
    Path workDir;

    @Test
    void prefixesBucketOntoRegionalEndpoint() {
        var configuration = createConfiguration("https://cos.ap-guangzhou.myqcloud.com");

        assertThat(configuration.getEndpointBuilder().buildGeneralApiEndpoint("bucket"))
                .isEqualTo("bucket.cos.ap-guangzhou.myqcloud.com");
        assertThat(configuration.getHttpProtocol()).isEqualTo(HttpProtocol.https);
    }

    @Test
    void keepsBucketQualifiedEndpoint() {
        var configuration = createConfiguration("https://bucket.cos.ap-guangzhou.myqcloud.com");

        assertThat(configuration.getEndpointBuilder().buildGeneralApiEndpoint("bucket"))
                .isEqualTo("bucket.cos.ap-guangzhou.myqcloud.com");
    }

    @Test
    void usesHttpProtocolForPlainHttpEndpoint() {
        var configuration = createConfiguration("http://cos.example.com");

        assertThat(configuration.getEndpointBuilder().buildGeneralApiEndpoint("bucket"))
                .isEqualTo("bucket.cos.example.com");
        assertThat(configuration.getHttpProtocol()).isEqualTo(HttpProtocol.http);
    }

    private ClientConfig createConfiguration(String server) {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("cos");
        policy.getSpec().setType(StoragePolicyType.COS);
        policy.getSpec().setBucketName("bucket");
        policy.getSpec().setServer(server);
        policy.getSpec().getCredentials().setAccessKey("access-key");
        policy.getSpec().getCredentials().setSecretKey("secret-key");
        var captured = new AtomicReference<ClientConfig>();
        try (var construction = mockConstruction(COSClient.class, (mock, context) ->
                captured.set((ClientConfig) context.arguments().get(1)))) {
            new CosStorageDriverFactory(properties)
                    .create(new ResolvedStoragePolicy(policy, policy.getSpec().getCredentials()));
        }
        return captured.get();
    }
}