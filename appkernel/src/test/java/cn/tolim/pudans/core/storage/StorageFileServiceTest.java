package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.PageRequest;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class StorageFileServiceTest {

    @Mock
    ReactiveExtensionClient client;

    private StorageFileService service;

    @BeforeEach
    void setUp() {
        service = new StorageFileService(client);
    }

    @Test
    void assignsAGeneratedNameOnCreate() {
        when(client.create(any(StorageFile.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var created = service.create(new StorageFile()).block();

        assertThat(created).isNotNull();
        assertThat(created.getMetadata().getName()).satisfies(name -> UUID.fromString(name));
    }

    @Test
    void buildsFiltersForTheFileQuery() {
        when(client.listBy(eq(StorageFile.class), any(ListOptions.class), any(PageRequest.class)))
                .thenReturn(Mono.just(new ListResult<>(1, 20, 0, List.of())));

        service.list(new StorageFileQuery("policy-1", "owner-1", "chat", StorageFileState.ACTIVE, "pdf", 1, 20))
                .block();

        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(StorageFile.class), options.capture(), any(PageRequest.class));
        assertThat(options.getValue().toString())
                .contains("spec.state", "spec.policy", "spec.owner", "spec.module", "spec.extension");
    }

    @Test
    void checksWhetherAPolicyIsReferenced() {
        when(client.countBy(eq(StorageFile.class), any(ListOptions.class))).thenReturn(Mono.just(1L));

        assertThat(service.existsByPolicy("policy-1").block()).isTrue();
    }

    @Test
    void listsExpiredUploads() {
        var file = new StorageFile();
        when(client.listBy(eq(StorageFile.class), any(ListOptions.class), any(PageRequest.class)))
                .thenReturn(Mono.just(new ListResult<>(1, 100, 1, List.of(file))));

        var result = service.listExpiredUploads(Instant.now(), 100).collectList().block();

        assertThat(result).containsExactly(file);
    }
}
