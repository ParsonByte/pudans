package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    StorageFileService fileService;

    @Mock
    StorageUploadService uploadService;

    @Mock
    StorageDownloadService downloadService;

    @Mock
    StorageDriverResolver driverResolver;

    @Mock
    ParameterSettingGroupService settingsService;

    private StorageService service;

    @BeforeEach
    void setUp() {
        service = new StorageService(fileService, uploadService, downloadService, driverResolver, settingsService);
    }

    @Test
    void movesAFileToTheRecycleBin() {
        var file = file(StorageFileState.ACTIVE);
        when(fileService.get("file-1")).thenReturn(Mono.just(file));
        when(settingsService.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(fileService.update(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.trash("file-1").block();

        assertThat(result).isNotNull();
        assertThat(result.getSpec().getState()).isEqualTo(StorageFileState.TRASHED);
        assertThat(result.getSpec().getTrashedAt()).isNotNull();
        assertThat(result.getSpec().getTrashExpiresAt()).isNotNull();
    }

    @Test
    void restoresAFileFromTheRecycleBin() {
        var file = file(StorageFileState.TRASHED);
        file.getSpec().setTrashedAt(Instant.now());
        file.getSpec().setTrashExpiresAt(Instant.now().plusSeconds(3600));
        when(fileService.get("file-1")).thenReturn(Mono.just(file));
        when(fileService.update(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var result = service.restore("file-1").block();

        assertThat(result).isNotNull();
        assertThat(result.getSpec().getState()).isEqualTo(StorageFileState.ACTIVE);
        assertThat(result.getSpec().getTrashedAt()).isNull();
        assertThat(result.getSpec().getTrashExpiresAt()).isNull();
    }

    @Test
    void purgesAFileAndItsObject() {
        var file = file(StorageFileState.TRASHED);
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        var driver = mock(StorageDriver.class);
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        when(driver.delete(List.of("uploads/a.txt"))).thenReturn(Mono.empty());
        when(fileService.delete(file)).thenReturn(Mono.empty());

        service.purge("file-1").block();

        verify(driver).delete(List.of("uploads/a.txt"));
        verify(fileService).delete(file);
    }

    @Test
    void purgesTheRecordBeforeDeletingTheObject() {
        var file = file(StorageFileState.TRASHED);
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        var driver = mock(StorageDriver.class);
        when(fileService.delete(file)).thenReturn(Mono.empty());
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        when(driver.delete(List.of("uploads/a.txt"))).thenReturn(Mono.empty());

        service.purge("file-1").block();

        var order = inOrder(fileService, driver);
        order.verify(fileService).delete(file);
        order.verify(driver).delete(List.of("uploads/a.txt"));
    }

    @Test
    void keepsTheObjectWhenTheRecordWasRestoredConcurrently() {
        var file = file(StorageFileState.TRASHED);
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        when(fileService.delete(file))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("file-1")));

        assertThatThrownBy(() -> service.purge("file-1").block())
                .isInstanceOf(OptimisticLockingFailureException.class);
        verifyNoInteractions(driverResolver);
    }

    @Test
    void rejectsPurgingAnActiveFile() {
        var file = file(StorageFileState.ACTIVE);
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));

        assertThatThrownBy(() -> service.purge("file-1").block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(driverResolver);
    }

    private static StorageFile file(StorageFileState state) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        file.getSpec().setPolicy("local");
        file.getSpec().setStorageKey("uploads/a.txt");
        file.getSpec().setState(state);
        return file;
    }
}
