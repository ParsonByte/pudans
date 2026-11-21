package cn.tolim.pudans.core.storage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.infra.properties.PudansProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageCleanupTaskTest {

    @Mock
    FileRecords fileRecords;

    @Mock
    Uploads uploads;

    @Mock
    StorageTransfer storageTransfer;

    @Test
    void abortsExpiredUploadsAndPurgesExpiredTrash() {
        var task = new StorageCleanupTask(fileRecords, uploads, storageTransfer, new PudansProperties());
        when(fileRecords.listExpiredUploads(any(Instant.class), eq(100)))
                .thenReturn(Flux.just(file("upload-1")));
        when(fileRecords.listExpiredTrash(any(Instant.class), eq(100)))
                .thenReturn(Flux.just(file("trash-1")));
        when(uploads.abort("upload-1")).thenReturn(Mono.empty());
        when(storageTransfer.purge("trash-1")).thenReturn(Mono.empty());

        task.cleanup().block();

        verify(uploads).abort("upload-1");
        verify(storageTransfer).purge("trash-1");
    }

    private static StorageFile file(String name) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName(name);
        file.setMetadata(metadata);
        return file;
    }
}
