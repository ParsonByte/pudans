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
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.infra.properties.PudansProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageCleanupTaskTest {

    @Mock
    StorageFileService fileService;

    @Mock
    StorageUploadService uploadService;

    @Mock
    StorageService storageService;

    @Test
    void abortsExpiredUploadsAndPurgesExpiredTrash() {
        var task = new StorageCleanupTask(fileService, uploadService, storageService, new PudansProperties());
        when(fileService.listExpiredUploads(any(Instant.class), eq(100)))
                .thenReturn(Flux.just(file("upload-1")));
        when(fileService.listExpiredTrash(any(Instant.class), eq(100)))
                .thenReturn(Flux.just(file("trash-1")));
        when(uploadService.abort("upload-1")).thenReturn(Mono.empty());
        when(storageService.purge("trash-1")).thenReturn(Mono.empty());

        task.cleanup().block();

        verify(uploadService).abort("upload-1");
        verify(storageService).purge("trash-1");
    }

    private static StorageFile file(String name) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName(name);
        file.setMetadata(metadata);
        return file;
    }
}
