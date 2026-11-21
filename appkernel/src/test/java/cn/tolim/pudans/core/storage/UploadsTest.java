package cn.tolim.pudans.core.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.contract.storage.resource.StoragePolicy;
import cn.tolim.pudans.contract.storage.resource.StoragePolicyType;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.DirectUploadCredential;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.NativeMultipartUpload;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StoragePutRequest;
import cn.tolim.pudans.core.storage.upload.StorageUploadRequest;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class UploadsTest {

    @Mock
    FileRecords fileService;

    @Mock
    Policies policyService;

    @Mock
    StorageDriverResolver driverResolver;

    @Mock
    StoragePolicyValidator validator;

    @Mock
    StoragePolicyPathGenerator pathGenerator;

    @Mock
    TransferTokens tokenService;

    @Mock
    ParameterGroups settingsService;

    @TempDir
    Path workDir;

    private Uploads service;

    @BeforeEach
    void setUp() {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        service = new Uploads(
                fileService,
                policyService,
                driverResolver,
                validator,
                pathGenerator,
                tokenService,
                settingsService,
                properties);
    }

    @Test
    void initiatesALocalUploadTicket() {
        var policy = policy();
        var driver = mock(StorageDriver.class);
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(settingsService.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(validator.chunkSize(policy)).thenReturn(8L * 1024L * 1024L);
        when(validator.partCount(10, 8L * 1024L * 1024L)).thenReturn(1);
        when(pathGenerator.generate(eq(policy), any(StoragePathContext.class))).thenReturn("uploads/key");
        when(fileService.create(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(tokenService.signUpload(any(), any(Duration.class))).thenReturn("token");

        var ticket = service
                .initiate(new StorageUploadRequest(
                        "local", "owner", "chat", "chat", "a.txt", "text/plain", 10, Map.of(), null))
                .block();

        assertThat(ticket).isNotNull();
        assertThat(ticket.policy()).isEqualTo("local");
        assertThat(ticket.storageKey()).isEqualTo("uploads/key");
        assertThat(ticket.chunkSize()).isEqualTo(8L * 1024L * 1024L);
        assertThat(ticket.partCount()).isEqualTo(1);
        assertThat(ticket.uploadToken()).isEqualTo("token");
        assertThat(ticket.presigned()).isFalse();
        assertThat(Files.isDirectory(workDir.resolve("uploads/.sessions").resolve(ticket.fileId())))
                .isTrue();
    }

    @Test
    void rejectsANullAttributeValue() {
        var policy = policy();
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("caption", null);
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(settingsService.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(validator.chunkSize(policy)).thenReturn(8L * 1024L * 1024L);
        when(validator.partCount(10, 8L * 1024L * 1024L)).thenReturn(1);
        when(pathGenerator.generate(eq(policy), any(StoragePathContext.class))).thenReturn("uploads/key");

        assertThatThrownBy(() -> service
                        .initiate(new StorageUploadRequest(
                                "local", "owner", "chat", "chat", "a.txt", "text/plain", 10, attributes, null))
                        .block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-attribute-invalid"));
        verifyNoInteractions(fileService);
    }

    @Test
    void initiatesADirectUpload() {
        var policy = policy();
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(DirectUploadDriver.class));
        var direct = (DirectUploadDriver) driver;
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(settingsService.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(validator.chunkSize(policy)).thenReturn(8L * 1024L * 1024L);
        when(validator.partCount(10, 8L * 1024L * 1024L)).thenReturn(1);
        when(pathGenerator.generate(eq(policy), any(StoragePathContext.class))).thenReturn("uploads/key");
        when(fileService.create(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(direct.createDirectUpload(any())).thenReturn(Mono.just(new DirectUploadCredential(
                "qiniu",
                "https://upload.qiniup.com",
                "provider-token",
                null,
                null,
                "uploads/key",
                "text/plain",
                Instant.now().plusSeconds(600))));
        when(tokenService.signUpload(any(), any(Duration.class))).thenReturn("token");

        var ticket = service
                .initiate(new StorageUploadRequest(
                        "local", "owner", "chat", "chat", "a.txt", "text/plain", 10, Map.of(), null))
                .block();

        assertThat(ticket).isNotNull();
        assertThat(ticket.directUpload()).isNotNull();
        assertThat(ticket.directUpload().provider()).isEqualTo("qiniu");
        assertThat(ticket.directUpload().token()).isEqualTo("provider-token");
    }

    @Test
    void completesADirectUpload() {
        var file = uploadingFile();
        var policy = policy();
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(DirectUploadDriver.class));
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        when(fileService.update(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(driver.stat("uploads/a.txt"))
                .thenReturn(Mono.just(new StorageObject("uploads/a.txt", 4, Instant.now(), "etag")));

        var result = service.complete("file-1", List.of()).block();

        assertThat(result).isNotNull();
        assertThat(result.getSpec().getState()).isEqualTo(StorageFileState.ACTIVE);
        assertThat(result.getSpec().getSize()).isEqualTo(4);
    }

    @Test
    void completesADirectUploadWithLocalParts() throws Exception {
        var file = uploadingFile();
        var policy = policy();
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(DirectUploadDriver.class));
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        when(fileService.update(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(driver.put(any(StoragePutRequest.class)))
                .thenReturn(Mono.just(new StorageObject("uploads/a.txt", 4, Instant.now(), "etag")));
        var directory = workDir.resolve("uploads/.sessions/file-1");
        Files.createDirectories(directory);
        Files.write(directory.resolve("part-1"), "data".getBytes(StandardCharsets.UTF_8));

        var result = service.complete("file-1", List.of()).block();

        assertThat(result).isNotNull();
        assertThat(result.getSpec().getState()).isEqualTo(StorageFileState.ACTIVE);
        verify(driver).put(any(StoragePutRequest.class));
        verify(driver, never()).stat("uploads/a.txt");
    }

    @Test
    void rejectsAnOversizedObjectWhenTheDeclaredSizeIsUnknown() {
        var file = uploadingFile();
        file.getSpec().setSize(0);
        var policy = policy();
        policy.getSpec().setMaxSize(3);
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(DirectUploadDriver.class));
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(driver.stat("uploads/a.txt"))
                .thenReturn(Mono.just(new StorageObject("uploads/a.txt", 4, Instant.now(), "etag")));
        when(driver.delete(List.of("uploads/a.txt"))).thenReturn(Mono.empty());

        assertThatThrownBy(() -> service.complete("file-1", List.of()).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-file-too-large"));
        verify(driver).delete(List.of("uploads/a.txt"));
    }

    @Test
    void keepsTheUploadRecordWhenAbortFails() {
        var file = uploadingFile();
        file.getSpec().setNativeUploadId("upload-1");
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(MultipartStorageDriver.class));
        var multipart = (MultipartStorageDriver) driver;
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        when(multipart.abortMultipart(any())).thenReturn(Mono.error(
                new ApiException(HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败。")));

        assertThatThrownBy(() -> service.abort("file-1").block()).isInstanceOf(ApiException.class);
        verify(fileService, never()).delete(any(StorageFile.class));
    }

    @Test
    void deletesDirectUploadObjectsOnAbort() {
        var file = uploadingFile();
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(DirectUploadDriver.class));
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        when(driverResolver.driver("local")).thenReturn(Mono.just(driver));
        when(driver.delete(List.of("uploads/a.txt"))).thenReturn(Mono.empty());
        when(fileService.delete(file)).thenReturn(Mono.empty());

        service.abort("file-1").block();

        verify(driver).delete(List.of("uploads/a.txt"));
        verify(fileService).delete(file);
    }

    @Test
    void rejectsAPartAboveTheChunkSize() {
        var file = uploadingFile();
        file.getSpec().setChunkSize(4);
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));

        assertThatThrownBy(() -> service.putPart("file-1", 1, 5, Flux.empty()).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("storage-part-too-large"));
        verifyNoInteractions(driverResolver);
    }

    @Test
    void chunksAServerSideUploadAboveTheChunkSize() {
        var policy = policy();
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(MultipartStorageDriver.class));
        var multipart = (MultipartStorageDriver) driver;
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(settingsService.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(validator.chunkSize(policy)).thenReturn(4L);
        when(validator.partCount(10, 4L)).thenReturn(3);
        when(pathGenerator.generate(eq(policy), any(StoragePathContext.class))).thenReturn("uploads/key");
        when(fileService.create(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(fileService.update(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(driver.capabilities()).thenReturn(Set.of(StorageCapability.MULTIPART_UPLOAD));
        when(multipart.createMultipart(any()))
                .thenReturn(Mono.just(new NativeMultipartUpload("upload-1", "uploads/key", 4)));
        when(multipart.putPart(any(), anyInt(), any(), anyLong()))
                .thenAnswer(invocation -> Mono.just(new UploadedPart(
                        invocation.getArgument(1), "etag", invocation.getArgument(3))));
        when(multipart.completeMultipart(any(), any()))
                .thenReturn(Mono.just(new StorageObject("uploads/key", 10, Instant.now(), "etag")));
        var body = Flux.<org.springframework.core.io.buffer.DataBuffer>just(
                DefaultDataBufferFactory.sharedInstance.wrap(
                        ByteBuffer.wrap("abcdefghij".getBytes(StandardCharsets.UTF_8))));

        var result = service
                .upload(new StorageWriteRequest(
                        "local", "owner", "chat", "chat", "a.txt", "text/plain", 10, Map.of(), body, true))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.getSpec().getState()).isEqualTo(StorageFileState.ACTIVE);
        verify(multipart, times(3)).putPart(any(), anyInt(), any(), anyLong());
        verify(multipart).completeMultipart(any(), any());
    }

    @Test
    void initiatesANativeMultipartUpload() {        var policy = policy();
        var driver = mock(
                StorageDriver.class, withSettings().extraInterfaces(MultipartStorageDriver.class));
        var multipart = (MultipartStorageDriver) driver;
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(settingsService.getStorage()).thenReturn(Mono.just(new ParameterSetting.Storage()));
        when(validator.chunkSize(policy)).thenReturn(8L * 1024L * 1024L);
        when(validator.partCount(10, 8L * 1024L * 1024L)).thenReturn(1);
        when(pathGenerator.generate(eq(policy), any(StoragePathContext.class))).thenReturn("uploads/key");
        when(fileService.create(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(fileService.update(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(driver.capabilities()).thenReturn(Set.of(StorageCapability.MULTIPART_UPLOAD));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(multipart.createMultipart(any()))
                .thenReturn(Mono.just(new NativeMultipartUpload("upload-1", "uploads/key", 8L * 1024L * 1024L)));
        when(tokenService.signUpload(any(), any(Duration.class))).thenReturn("token");

        var ticket = service
                .initiate(new StorageUploadRequest(
                        "local", "owner", "chat", "chat", "a.txt", "text/plain", 10, Map.of(), null))
                .block();

        assertThat(ticket).isNotNull();
        assertThat(ticket.presigned()).isFalse();
        verify(multipart).createMultipart(any());
    }

    @Test
    void completesALocalUploadAndActivatesTheFile() throws Exception {
        var file = uploadingFile();
        var policy = policy();
        var driver = mock(StorageDriver.class);
        when(policyService.requireReady("local")).thenReturn(Mono.just(policy));
        when(fileService.fetch("file-1")).thenReturn(Mono.just(file));
        when(fileService.update(any(StorageFile.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(driverResolver.driver(policy)).thenReturn(Mono.just(driver));
        when(driver.put(any(StoragePutRequest.class)))
                .thenReturn(Mono.just(new StorageObject("uploads/a.txt", 4, Instant.now(), "etag")));
        var directory = workDir.resolve("uploads/.sessions/file-1");
        Files.createDirectories(directory);
        Files.write(directory.resolve("part-1"), "data".getBytes());

        var result = service
                .complete("file-1", List.of(new UploadedPart(1, "etag", 4)))
                .block();

        assertThat(result).isNotNull();
        assertThat(result.getSpec().getState()).isEqualTo(StorageFileState.ACTIVE);
        assertThat(result.getSpec().getSize()).isEqualTo(4);
        assertThat(Files.notExists(directory)).isTrue();
        verify(driver).put(any(StoragePutRequest.class));
    }

    private static StoragePolicy policy() {
        var policy = new StoragePolicy();
        var metadata = new Metadata();
        metadata.setName("local");
        policy.setMetadata(metadata);
        var spec = policy.getSpec();
        spec.setDisplayName("local");
        spec.setType(StoragePolicyType.LOCAL);
        spec.setDirNameRule("uploads/{path}");
        spec.setFileNameRule("{randomkey8}_{originname}");
        return policy;
    }

    private static StorageFile uploadingFile() {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName("file-1");
        file.setMetadata(metadata);
        var spec = file.getSpec();
        spec.setPolicy("local");
        spec.setStorageKey("uploads/a.txt");
        spec.setOriginalName("a.txt");
        spec.setContentType("text/plain");
        spec.setSize(4);
        spec.setState(StorageFileState.UPLOADING);
        spec.setChunkSize(8L * 1024L * 1024L);
        spec.setPartCount(1);
        spec.setUploadExpiresAt(Instant.now().plus(Duration.ofHours(1)));
        return file;
    }
}
