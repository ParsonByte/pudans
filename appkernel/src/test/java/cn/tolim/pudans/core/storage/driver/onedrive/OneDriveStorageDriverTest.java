package cn.tolim.pudans.core.storage.driver.onedrive;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.StorageCapability;

class OneDriveStorageDriverTest {

    @TempDir
    Path workDir;

    @Test
    void exposesOneDriveTypeAndCapabilities() {
        var driver = new OneDriveStorageDriver(policy(), workDir);

        assertThat(driver.type()).isEqualTo(StoragePolicyType.ONEDRIVE);
        assertThat(driver.capabilities())
                .contains(StorageCapability.PRESIGNED_SOURCE, StorageCapability.RANGE_READ);
        driver.close();
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("onedrive");
        policy.getSpec().setType(StoragePolicyType.ONEDRIVE);
        policy.getSpec().setBucketName("client-id");
        policy.getSpec().getSettings().setTenantId("common");
        policy.getSpec().getCredentials().setClientSecret("client-secret");
        policy.getSpec().getCredentials().setToken("refresh-token");
        return policy;
    }
}
