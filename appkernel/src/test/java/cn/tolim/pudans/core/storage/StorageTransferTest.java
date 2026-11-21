package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.infra.api.ParameterSetting;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageTransferTest {

    @Mock
    FileRecords fileRecords;

    @Mock
    Uploads uploadService;

    @Mock
    Downloads downloads;

    @Mock
    StorageDriverResolver driverResolver;

    @Mock
    ParameterGroups parameterGroups;

    private StorageTransfer service;

    @BeforeEach
    void setUp() {
        service = new StorageTransfer(fileRecords, uploadService, downloads, driverResolver, parameterGroups);
    }

    @Test
    void movesAFileToTheRecycleBin() {
        var file = file(StorageFileState.ACTIVE);
        when(fileRecords.get("file-1")).thenReturn(Mono.just(file));
        when(parameterGroups.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(fileRecords.update(any(StorageFile.class)))
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
        when(fileRecords.get("file-1")).thenReturn(Mono.just(file));
        when(fileRecords.update(any(StorageFile.class)))
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
        when(fileRecords.fetch("file-1")).thenReturn(Mono.just(file));
        var driver = mock(StorageDriver.class);
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        when(driver.delete(List.of("uploads/a.txt"))).thenReturn(Mono.empty());
        when(fileRecords.delete(file)).thenReturn(Mono.empty());

        service.purge("file-1").block();

        verify(driver).delete(List.of("uploads/a.txt"));
        verify(fileRecords).delete(file);
    }

    @Test
    void purgesTheRecordBeforeDeletingTheObject() {
        var file = file(StorageFileState.TRASHED);
        when(fileRecords.fetch("file-1")).thenReturn(Mono.just(file));
        var driver = mock(StorageDriver.class);
        when(fileRecords.delete(file)).thenReturn(Mono.empty());
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        when(driver.delete(List.of("uploads/a.txt"))).thenReturn(Mono.empty());

        service.purge("file-1").block();

        var order = inOrder(fileRecords, driver);
        order.verify(fileRecords).delete(file);
        order.verify(driver).delete(List.of("uploads/a.txt"));
    }

    @Test
    void keepsTheObjectWhenTheRecordWasRestoredConcurrently() {
        var file = file(StorageFileState.TRASHED);
        when(fileRecords.fetch("file-1")).thenReturn(Mono.just(file));
        when(fileRecords.delete(file))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("file-1")));

        assertThatThrownBy(() -> service.purge("file-1").block())
                .isInstanceOf(OptimisticLockingFailureException.class);
        verifyNoInteractions(driverResolver);
    }

    @Test
    void rejectsPurgingAnActiveFile() {
        var file = file(StorageFileState.ACTIVE);
        when(fileRecords.fetch("file-1")).thenReturn(Mono.just(file));

        assertThatThrownBy(() -> service.purge("file-1").block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(driverResolver);
    }

    @Test
    void readsFileBytesWithLimit() {
        var content = "hello".getBytes(StandardCharsets.UTF_8);
        when(downloads.open(eq("file-1"), any(StorageReadOptions.class)))
                .thenReturn(Mono.just(new StorageReadResult(
                        new StorageObject("uploads/a.txt", content.length, Instant.now(), "etag"),
                        0,
                        content.length - 1,
                        content.length,
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(content)))));

        var bytes = service.readBytes("file-1", 1024).block();

        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("hello");
    }

    @Test
    void rejectsFileBytesAboveLimit() {
        var content = new byte[16];
        when(downloads.open(eq("file-1"), any(StorageReadOptions.class)))
                .thenReturn(Mono.just(new StorageReadResult(
                        new StorageObject("uploads/a.txt", content.length, Instant.now(), "etag"),
                        0,
                        content.length - 1,
                        content.length,
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(content)))));

        assertThatThrownBy(() -> service.readBytes("file-1", 8).block())
                .isInstanceOf(DataBufferLimitException.class);
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
