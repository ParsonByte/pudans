package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageDownloadServiceTest {

    @Mock
    StorageFileService fileService;

    @Mock
    StorageDriverResolver driverResolver;

    @Mock
    StorageTokenService tokenService;

    @Mock
    ParameterSettingGroupService settingsService;

    @Mock
    StoragePolicyService policyService;

    private StorageDownloadService service;

    @BeforeEach
    void setUp() {
        service = new StorageDownloadService(fileService, driverResolver, tokenService, settingsService, policyService);
    }

    @Test
    void createsADownloadUrl() {
        when(fileService.get("file-1")).thenReturn(Mono.just(activeFile()));
        when(settingsService.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(tokenService.signDownload(eq("file-1"), any(Duration.class))).thenReturn("token");

        var result = service.downloadUrl("file-1", null).block();

        assertThat(result).isNotNull();
        assertThat(result.url()).contains("/storagefiles/file-1/content?token=token");
    }

    @Test
    void buildsAPresignedSourceUrl() {
        when(fileService.get("file-1")).thenReturn(Mono.just(activeFile()));
        when(policyService.get("local")).thenReturn(Mono.just(policy(false)));
        var driver = mock(StorageDriver.class);
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        when(driver.capabilities()).thenReturn(java.util.Set.of(StorageCapability.PRESIGNED_SOURCE));
        when(driver.source(eq("uploads/a.txt"), any(StorageSourceOptions.class)))
                .thenReturn(Mono.just(new StorageSource(URI.create("https://example.com/a"), Instant.now(), false)));

        var result = service
                .sourceUrl("file-1", new StorageSourceOptions(true, "a.txt", Duration.ofMinutes(5)))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.url()).hasToString("https://example.com/a");
    }

    @Test
    void opensAFile() {
        when(fileService.get("file-1")).thenReturn(Mono.just(activeFile()));
        var driver = mock(StorageDriver.class);
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        var expected = new StorageReadResult(
                new StorageObject("uploads/a.txt", 4, Instant.now(), "etag"), 0, 3, 4, Flux.empty());
        when(driver.get(eq("uploads/a.txt"), any(StorageReadOptions.class))).thenReturn(Mono.just(expected));

        var result = service.open("file-1", StorageReadOptions.full()).block();

        assertThat(result).isSameAs(expected);
    }

    private static StorageFile activeFile() {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        file.getSpec().setPolicy("local");
        file.getSpec().setStorageKey("uploads/a.txt");
        file.getSpec().setState(StorageFileState.ACTIVE);
        return file;
    }

    private static StoragePolicy policy(boolean proxyDownload) {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName("local");
        policy.setMetadata(metadata);
        policy.getSpec().setDisplayName("local");
        policy.getSpec().setType(StoragePolicyType.LOCAL);
        policy.getSpec().setProxyDownload(proxyDownload);
        return policy;
    }
}
