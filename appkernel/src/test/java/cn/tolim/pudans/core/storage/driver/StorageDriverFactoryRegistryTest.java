package cn.tolim.pudans.core.storage.driver;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.cos.CosStorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.local.LocalStorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.obs.ObsStorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.onedrive.OneDriveStorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.oss.OssStorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.qiniu.QiniuStorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.s3.S3StorageDriverFactory;
import cn.tolim.pudans.core.storage.driver.upyun.UpyunStorageDriverFactory;
import cn.tolim.pudans.infra.properties.PudansProperties;

class StorageDriverFactoryRegistryTest {

    @Test
    void registersEverySupportedStorageType() {
        var properties = new PudansProperties();
        properties.setWorkDir(Path.of("."));
        var registry = new StorageDriverFactoryRegistry(List.of(
                new LocalStorageDriverFactory(properties),
                new S3StorageDriverFactory(),
                new OssStorageDriverFactory(properties),
                new CosStorageDriverFactory(properties),
                new ObsStorageDriverFactory(properties),
                new QiniuStorageDriverFactory(properties),
                new UpyunStorageDriverFactory(properties),
                new OneDriveStorageDriverFactory(properties)));

        assertThat(registry.descriptors())
                .extracting(StoragePolicyTypeDescriptor::type)
                .containsExactlyInAnyOrder(StoragePolicyType.values());
        for (var type : StoragePolicyType.values()) {
            assertThat(registry.factory(type)).as("factory for " + type).isPresent();
        }
    }
}
