package cn.tolim.pudans.repository.service;

import cn.tolim.pudans.repository.contract.DownloadUrlResponse;
import cn.tolim.pudans.repository.contract.FileMetadataResponse;
import cn.tolim.pudans.repository.contract.FilePageResponse;
import cn.tolim.pudans.repository.contract.RepositoryApiPaths;
import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.entity.FileStatus;
import cn.tolim.pudans.repository.entity.RepositoryFileEntity;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.pudans.repository.repository.RepositoryFileRepository;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.local.LocalFileSystemStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceTests {

    @TempDir
    Path tempDir;

    private RepositoryFileRepository repository;
    private RepositoryProperties properties;
    private FileStorageService service;
    private LocalFileSystemStorage localStorage;

    @BeforeEach
    void setUp() {
        repository = mock(RepositoryFileRepository.class);
        properties = new RepositoryProperties();
        properties.setDefaultTenantId("tenant-default");
        properties.setDefaultCreatedBy("tester");
        properties.setMaxFileSize(1024);
        properties.getLocal().setBaseDirectory(tempDir.resolve("repository").toString());
        properties.getChecksum().setEnabled(false);
        localStorage = new LocalFileSystemStorage(properties.getLocal());
        ObjectStorageRegistry registry = ObjectStorageRegistry.of(
                Map.of(StorageProviderType.LOCAL, localStorage), StorageProviderType.LOCAL);
        service = new FileStorageService(repository, registry,
                new ChecksumService(null, properties), properties);
        when(repository.save(any(RepositoryFileEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void uploadsObjectAndPersistsMetadata() throws IOException {
        byte[] content = "repository content".getBytes(StandardCharsets.UTF_8);

        FileMetadataResponse response = command("notes.txt", "text/plain",
                content, null);

        assertThat(response.id()).isNotBlank();
        assertThat(response.tenantId()).isEqualTo("tenant-default");
        assertThat(response.provider()).isEqualTo("local");
        assertThat(response.originalName()).isEqualTo("notes.txt");
        assertThat(response.contentType()).isEqualTo("text/plain");
        assertThat(response.extension()).isEqualTo("txt");
        assertThat(response.size()).isEqualTo(content.length);
        assertThat(response.status()).isEqualTo(FileStatus.ACTIVE.name());
        assertThat(response.createdBy()).isEqualTo("tester");
        assertThat(response.objectKey()).endsWith(".txt");

        RepositoryFileEntity entity = captureSavedEntity();
        Path stored = localStorage.root().resolve(entity.getObjectKey());
        assertThat(Files.readAllBytes(stored)).isEqualTo(content);
    }

    @Test
    void downloadsContentForOwningTenant() throws IOException {
        byte[] content = "download me".getBytes(StandardCharsets.UTF_8);
        FileMetadataResponse response = command("download.txt", "text/plain",
                content, "tenant-a");
        RepositoryFileEntity entity = captureSavedEntity();
        when(repository.findByIdAndTenantIdAndDeleteTimeIsNull(entity.getId(), "tenant-a"))
                .thenReturn(Optional.of(entity));

        FileContent file = service.download(response.id(), "tenant-a");

        try (InputStream input = file.content()) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }
        assertThat(file.file().getOriginalName()).isEqualTo("download.txt");
    }

    @Test
    void marksFileDeletedAndRemovesStoredObject() {
        byte[] content = "delete me".getBytes(StandardCharsets.UTF_8);
        command("delete.txt", "text/plain", content, null);
        RepositoryFileEntity entity = captureSavedEntity();
        when(repository.findByIdAndTenantIdAndDeleteTimeIsNull(entity.getId(), "tenant-default"))
                .thenReturn(Optional.of(entity));

        service.delete(entity.getId(), null);

        assertThat(entity.getStatus()).isEqualTo(FileStatus.DELETED);
        assertThat(entity.getDeleteTime()).isNotNull();
        assertThat(Files.exists(localStorage.root().resolve(entity.getObjectKey()))).isFalse();
        verify(repository, times(2)).save(entity);
    }

    @Test
    void rejectsUnknownFile() {
        when(repository.findByIdAndTenantIdAndDeleteTimeIsNull(anyString(), anyString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.metadata("missing-id", "tenant-default"))
                .isInstanceOfSatisfying(RepositoryException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_NOT_FOUND"));
    }

    @Test
    void listsFilesForTenant() {
        RepositoryFileEntity entity = new RepositoryFileEntity();
        entity.setId("file-1");
        entity.setTenantId("tenant-default");
        entity.setOriginalName("a.txt");
        entity.setProvider(StorageProviderType.LOCAL);
        entity.setObjectKey("tenant-default/a.txt");
        entity.setSize(1);
        entity.setStatus(FileStatus.ACTIVE);
        when(repository.search(anyString(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 10), 1));

        FilePageResponse page = service.list(new FileQuery(null, null, null, null, 0, 10));

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().getFirst().id()).isEqualTo("file-1");
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.page()).isZero();
    }

    @Test
    void enforcesMaximumFileSize() {
        properties.setMaxFileSize(4);
        byte[] content = "too large".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> command("large.txt", "text/plain", content, null))
                .isInstanceOfSatisfying(RepositoryException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo("FILE_TOO_LARGE"));
    }

    @Test
    void enforcesAllowedContentTypes() {
        properties.getAllowedContentTypes().add("image/*");
        byte[] content = "plain".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> command("note.txt", "text/plain", content, null))
                .isInstanceOfSatisfying(RepositoryException.class,
                        exception -> assertThat(exception.getCode())
                                .isEqualTo("CONTENT_TYPE_NOT_ALLOWED"));

        FileMetadataResponse response = command("image.png", "image/png", content,
                null);
        assertThat(response.contentType()).isEqualTo("image/png");
    }

    @Test
    void exposesStreamingUrlForLocalStorage() {
        byte[] content = "url".getBytes(StandardCharsets.UTF_8);
        FileMetadataResponse response = command("url.txt", "text/plain", content,
                null);
        RepositoryFileEntity entity = captureSavedEntity();
        when(repository.findByIdAndTenantIdAndDeleteTimeIsNull(entity.getId(), "tenant-default"))
                .thenReturn(Optional.of(entity));

        DownloadUrlResponse url = service.downloadUrl(entity.getId(), null);

        assertThat(url.direct()).isFalse();
        assertThat(url.url()).isEqualTo(RepositoryApiPaths.fileContentPath(entity.getId()));
        assertThat(url.expiresAt()).isNull();
    }

    @Test
    void sanitizesPathSegmentsAndOriginalName() {
        byte[] content = "safe".getBytes(StandardCharsets.UTF_8);
        service.upload(new FileUploadCommand(null, "../../etc", null, "../unsafe name.txt",
                "text/plain", content.length, null, Map.of("owner", "tester"), null,
                () -> new ByteArrayInputStream(content)));

        RepositoryFileEntity entity = captureSavedEntity();
        assertThat(entity.getOriginalName()).isEqualTo("unsafe name.txt");
        assertThat(entity.getAppCode()).isEqualTo("../../etc");
        assertThat(entity.getObjectKey()).doesNotContain("..");
    }

    private FileMetadataResponse command(String name, String contentType, byte[] content,
                                         String tenantId) {
        return service.upload(new FileUploadCommand(tenantId, "app", "docs", name, contentType,
                content.length, null, Map.of(), null, () -> new ByteArrayInputStream(content)));
    }

    private RepositoryFileEntity captureSavedEntity() {
        ArgumentCaptor<RepositoryFileEntity> captor = ArgumentCaptor
                .forClass(RepositoryFileEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}