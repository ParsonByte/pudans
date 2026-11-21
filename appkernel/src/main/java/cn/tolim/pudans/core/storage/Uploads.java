package cn.tolim.pudans.core.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.DirectUploadCredential;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.StorageDirectUploadRequest;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.NativeMultipartUpload;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.StorageMultipartRequest;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadPartUrl;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageIo;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StoragePutRequest;
import cn.tolim.pudans.core.storage.upload.StorageUploadRequest;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.core.storage.upload.UploadStatus;
import cn.tolim.pudans.core.storage.upload.UploadTicket;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Service
@RequiredArgsConstructor
public class Uploads {

    private static final String SESSION_DIRECTORY = "uploads/.sessions";

    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private static final int MAX_ATTRIBUTES = 32;

    private static final int MAX_ATTRIBUTE_LENGTH = 4096;

    private final FileRecords fileRecords;

    private final Policies policies;

    private final StorageDriverResolver driverResolver;

    private final StoragePolicyValidator validator;

    private final StoragePolicyPathGenerator pathGenerator;

    private final TransferTokens transferTokens;

    private final ParameterGroups parameterGroups;

    private final PudansProperties properties;

    public Mono<UploadTicket> initiate(StorageUploadRequest request) {
        return resolvePolicy(request.policy())
                .flatMap(policy -> parameterGroups
                        .getStorage()
                        .flatMap(settings -> createUpload(policy, request, settings)));
    }

    public void verifyUploadToken(String token, String fileId) {
        transferTokens.verifyUpload(token, fileId);
    }

    public Mono<String> issueToken(String fileId) {
        return requireUploading(fileId).map(file -> {
            var expiresAt = file.getSpec().getUploadExpiresAt();
            if (expiresAt == null) {
                throw expired();
            }
            var remaining = Duration.between(Instant.now(), expiresAt);
            if (remaining.isNegative() || remaining.isZero()) {
                throw expired();
            }
            return transferTokens.signUpload(fileId, remaining);
        });
    }

    public Mono<UploadStatus> status(String fileId) {
        return requireUploading(fileId).flatMap(file -> driverResolver
                .driver(file.getSpec().getPolicy())
                .flatMap(driver -> uploadedParts(file, driver)
                        .map(parts -> new UploadStatus(
                                fileId,
                                file.getSpec().getPolicy(),
                                file.getSpec().getStorageKey(),
                                file.getSpec().getChunkSize(),
                                file.getSpec().getPartCount(),
                                file.getSpec().getUploadExpiresAt(),
                                parts,
                                presigned(driver)))));
    }

    public Mono<UploadedPart> putPart(String fileId, int partNumber, long contentLength, Flux<DataBuffer> body) {
        return requireUploading(fileId).flatMap(file -> {
            if (partNumber < 1 || partNumber > file.getSpec().getPartCount()) {
                return Mono.error(partInvalid());
            }
            var maxPartSize = file.getSpec().getChunkSize();
            if (maxPartSize > 0 && contentLength > maxPartSize) {
                return Mono.error(partTooLarge());
            }
            return driverResolver.driver(file.getSpec().getPolicy()).flatMap(driver -> {
                if (driver instanceof MultipartStorageDriver multipart && hasNativeUpload(file)) {
                    return multipart.putPart(nativeUpload(file), partNumber, limit(body, maxPartSize), contentLength);
                }
                return writeLocalPart(fileId, partNumber, limit(body, maxPartSize));
            });
        });
    }

    public Mono<UploadPartUrl> partUrl(String fileId, int partNumber) {
        return requireUploading(fileId).flatMap(file -> {
            if (partNumber < 1 || partNumber > file.getSpec().getPartCount()) {
                return Mono.error(partInvalid());
            }
            return driverResolver.driver(file.getSpec().getPolicy()).flatMap(driver -> {
                if (!(driver instanceof MultipartStorageDriver multipart)
                        || !hasNativeUpload(file)
                        || !driver.capabilities().contains(StorageCapability.PRESIGNED_PARTS)) {
                    return Mono.error(new ApiException(
                            HttpStatus.BAD_REQUEST, "storage-presign-unsupported", "当前存储不支持预签名分片。"));
                }
                return multipart.presignPart(nativeUpload(file), partNumber, Duration.ofMinutes(10));
            });
        });
    }

    public Mono<StorageFile> complete(String fileId, List<UploadedPart> parts) {
        return fileRecords.fetch(fileId)
                .switchIfEmpty(Mono.error(uploadNotFound()))
                .flatMap(file -> {
                    if (file.getSpec().getState() == StorageFileState.ACTIVE) {
                        return Mono.just(file);
                    }
                    if (file.getSpec().getState() != StorageFileState.UPLOADING) {
                        return Mono.error(uploadStateInvalid());
                    }
                    var expiresAt = file.getSpec().getUploadExpiresAt();
                    if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                        return Mono.error(expired());
                    }
                    return policies
                            .requireReady(file.getSpec().getPolicy())
                            .flatMap(policy -> driverResolver.driver(policy).flatMap(driver -> {
                                if (driver instanceof DirectUploadDriver && !hasNativeUpload(file)) {
                                    return scanLocalParts(fileId)
                                            .flatMap(localParts -> localParts.isEmpty()
                                                    ? driver.stat(file.getSpec().getStorageKey())
                                                            .flatMap(object -> activate(policy, driver, file, object))
                                                    : finishUpload(file, driver, parts)
                                                            .flatMap(object -> activate(policy, driver, file, object)));
                                }
                                return finishUpload(file, driver, parts)
                                        .flatMap(object -> activate(policy, driver, file, object));
                            }));
                });
    }

    public Mono<Void> abort(String fileId) {
        return fileRecords.fetch(fileId).flatMap(file -> {
                    if (file.getSpec().getState() != StorageFileState.UPLOADING) {
                        return Mono.empty();
                    }
                    return driverResolver
                            .driver(file.getSpec().getPolicy())
                            .flatMap(driver -> abortDriver(file, driver))
                            .then(Mono.fromRunnable(() -> deleteSessionDirectory(fileId))
                                    .subscribeOn(Schedulers.boundedElastic()))
                            .then(Mono.defer(() -> fileRecords.delete(file)));
                })
                .then();
    }

    public Mono<StorageFile> upload(StorageWriteRequest request) {
        return resolvePolicy(request.policy())
                .flatMap(policy -> parameterGroups
                        .getStorage()
                        .flatMap(settings -> createWrite(policy, request, settings)));
    }

    private Mono<StorageFile> createWrite(
            StoragePolicy policy, StorageWriteRequest request, ParameterSetting.Storage settings) {
        return Mono.fromCallable(() -> {
                    validator.validateUpload(policy, request.originalName(), request.size());
                    return policy;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(validated -> {
                    var chunkSize = validator.chunkSize(policy);
                    var partCount = validator.partCount(request.size(), chunkSize);
                    var key = pathGenerator.generate(policy, new StoragePathContext(
                            request.owner(), request.originPath(), request.originalName(), Instant.now()));
                    var ttlHours = settings.getUploadSessionTtlHours() == null
                            ? 24
                            : settings.getUploadSessionTtlHours();
                    var file = uploadingFile(
                            policy,
                            request.owner(),
                            request.module(),
                            request.originalName(),
                            request.contentType(),
                            request.size(),
                            request.attributes(),
                            null,
                            key,
                            chunkSize,
                            partCount,
                            Instant.now().plus(Duration.ofHours(ttlHours)));
                    return fileRecords.create(file)
                            .flatMap(created -> driverResolver
                                    .driver(policy)
                                    .flatMap(driver -> putFile(driver, created, request, chunkSize, partCount)
                                            .flatMap(object -> activate(policy, driver, created, object)))
                                    .onErrorResume(error -> fileRecords.delete(created).then(Mono.error(error))));
                });
    }

    private Mono<StorageObject> putFile(
            StorageDriver driver, StorageFile file, StorageWriteRequest request, long chunkSize, int partCount) {
        var spec = file.getSpec();
        if (request.size() > chunkSize
                && driver instanceof MultipartStorageDriver multipart
                && driver.capabilities().contains(StorageCapability.MULTIPART_UPLOAD)) {
            return uploadMultipartBody(multipart, file, request, chunkSize, partCount);
        }
        return driver.put(new StoragePutRequest(
                spec.getStorageKey(), spec.getSize(), spec.getContentType(), request.body(), request.overwrite()));
    }

    private Mono<StorageObject> uploadMultipartBody(
            MultipartStorageDriver multipart,
            StorageFile file,
            StorageWriteRequest request,
            long chunkSize,
            int partCount) {
        return StorageIo.spool(request.body(), tempDirectory())
                .flatMap(path -> Mono.fromCallable(() -> Files.size(path))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(size -> {
                            if (request.size() > 0 && size != request.size()) {
                                StorageIo.deleteQuietly(path);
                                return Mono.error(new ApiException(
                                        HttpStatus.BAD_REQUEST,
                                        "storage-size-mismatch",
                                        "文件大小与声明不一致。"));
                            }
                            return multipart
                                    .createMultipart(new StorageMultipartRequest(
                                            file.getSpec().getStorageKey(), size, file.getSpec().getContentType()))
                                    .flatMap(upload -> uploadParts(multipart, upload, path, size, chunkSize, partCount)
                                            .flatMap(parts -> multipart.completeMultipart(upload, parts))
                                            .onErrorResume(error -> multipart
                                                    .abortMultipart(upload)
                                                    .onErrorResume(abortError -> Mono.empty())
                                                    .then(Mono.error(error))))
                                    .doFinally(signal -> StorageIo.deleteQuietly(path));
                        }));
    }

    private Mono<List<UploadedPart>> uploadParts(
            MultipartStorageDriver multipart,
            NativeMultipartUpload upload,
            Path path,
            long size,
            long chunkSize,
            int partCount) {
        return Flux.range(1, partCount)
                .concatMap(part -> {
                    var offset = (long) (part - 1) * chunkSize;
                    var length = Math.min(chunkSize, size - offset);
                    return multipart.putPart(upload, part, readRange(path, offset, length), length);
                })
                .collectList();
    }

    private Flux<DataBuffer> readRange(Path path, long offset, long length) {
        return Flux.defer(() -> {
            try {
                var input = Files.newInputStream(path);
                input.skipNBytes(offset);
                return StorageIo.read(input, length);
            } catch (IOException e) {
                return Flux.error(e);
            }
        });
    }

    private Path tempDirectory() {
        return properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
    }

    private Mono<UploadTicket> createUpload(
            StoragePolicy policy, StorageUploadRequest request, ParameterSetting.Storage settings) {
        return Mono.fromCallable(() -> {
                    validator.validateUpload(policy, request.originalName(), request.size());
                    return policy;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(validated -> {
                    var chunkSize = validator.chunkSize(policy);
                    var partCount = validator.partCount(request.size(), chunkSize);
                    var key = pathGenerator.generate(policy, new StoragePathContext(
                            request.owner(), request.originPath(), request.originalName(), Instant.now()));
                    var ttlHours = settings.getUploadSessionTtlHours() == null
                            ? 24
                            : settings.getUploadSessionTtlHours();
                    var expiresAt = Instant.now().plus(Duration.ofHours(ttlHours));
                    var file = uploadingFile(
                            policy,
                            request.owner(),
                            request.module(),
                            request.originalName(),
                            request.contentType(),
                            request.size(),
                            request.attributes(),
                            request.sha256(),
                            key,
                            chunkSize,
                            partCount,
                            expiresAt);
                    return fileRecords.create(file)
                            .flatMap(created -> driverResolver
                                    .driver(policy)
                                    .flatMap(driver -> prepareUpload(created, driver, expiresAt)));
                });
    }

    private StorageFile uploadingFile(
            StoragePolicy policy,
            String owner,
            String module,
            String originalName,
            String contentType,
            long size,
            Map<String, String> attributes,
            String sha256,
            String key,
            long chunkSize,
            int partCount,
            Instant expiresAt) {
        var file = new StorageFile();
        var metadata = new Metadata();
        metadata.setName(UUID.randomUUID().toString());
        file.setMetadata(metadata);
        var spec = file.getSpec();
        spec.setPolicy(policy.getMetadata().getName());
        spec.setStorageKey(key);
        spec.setOriginalName(StringUtils.hasText(originalName) ? originalName : "file");
        spec.setExtension(StoragePolicyValidator.normalizeExtension(originalName));
        spec.setContentType(StringUtils.hasText(contentType) ? contentType : "application/octet-stream");
        spec.setSize(size);
        spec.setOwner(owner);
        spec.setModule(module);
        spec.setAttributes(copyAttributes(attributes));
        spec.setState(StorageFileState.UPLOADING);
        spec.setSha256(sha256);
        spec.setChunkSize(chunkSize);
        spec.setPartCount(partCount);
        spec.setUploadExpiresAt(expiresAt);
        return file;
    }

    private Mono<UploadTicket> prepareUpload(StorageFile file, StorageDriver driver, Instant expiresAt) {
        var fileId = file.getMetadata().getName();
        if (driver instanceof DirectUploadDriver direct) {
            var request = new StorageDirectUploadRequest(
                    file.getSpec().getStorageKey(),
                    file.getSpec().getSize(),
                    file.getSpec().getContentType(),
                    expiresAt);
            return direct
                    .createDirectUpload(request)
                    .map(credential -> ticket(file, driver, expiresAt, credential))
                    .onErrorResume(error -> fileRecords.delete(file).then(Mono.error(error)));
        }
        if (driver instanceof MultipartStorageDriver multipart
                && driver.capabilities().contains(StorageCapability.MULTIPART_UPLOAD)) {
            var request = new StorageMultipartRequest(
                    file.getSpec().getStorageKey(), file.getSpec().getSize(), file.getSpec().getContentType());
            return multipart
                    .createMultipart(request)
                    .flatMap(multipartUpload -> {
                        file.getSpec().setNativeUploadId(multipartUpload.uploadId());
                        return fileRecords.update(file);
                    })
                    .map(saved -> ticket(saved, driver, expiresAt, null))
                    .onErrorResume(error -> fileRecords.delete(file).then(Mono.error(error)));
        }
        return Mono.fromRunnable(() -> createSessionDirectory(fileId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ticket(file, driver, expiresAt, null));
    }

    private UploadTicket ticket(
            StorageFile file, StorageDriver driver, Instant expiresAt, DirectUploadCredential directUpload) {
        var fileId = file.getMetadata().getName();
        var remaining = Duration.between(Instant.now(), expiresAt);
        if (remaining.isNegative() || remaining.isZero()) {
            remaining = Duration.ofSeconds(1);
        }
        return new UploadTicket(
                fileId,
                file.getSpec().getPolicy(),
                file.getSpec().getStorageKey(),
                file.getSpec().getChunkSize(),
                file.getSpec().getPartCount(),
                expiresAt,
                transferTokens.signUpload(fileId, remaining),
                presigned(driver),
                directUpload);
    }

    private Mono<StorageObject> finishUpload(
            StorageFile file, StorageDriver driver, List<UploadedPart> parts) {
        var key = file.getSpec().getStorageKey();
        if (driver instanceof MultipartStorageDriver multipart && hasNativeUpload(file)) {
            Mono<List<UploadedPart>> uploaded = CollectionUtils.isEmpty(parts)
                    ? multipart.listParts(nativeUpload(file))
                    : Mono.just(parts);
            return uploaded
                    .flatMap(list -> {
                        var sorted = list.stream()
                                .sorted(Comparator.comparingInt(UploadedPart::partNumber))
                                .toList();
                        validateParts(file, sorted);
                        return multipart.completeMultipart(nativeUpload(file), sorted);
                    })
                    .flatMap(ignored -> driver.stat(key));
        }
        return mergeLocalParts(file)
                .flatMap(merged -> driver.put(new StoragePutRequest(
                                key,
                                file.getSpec().getSize(),
                                file.getSpec().getContentType(),
                                DataBufferUtils.read(
                                        new FileSystemResource(merged),
                                        DefaultDataBufferFactory.sharedInstance,
                                        READ_BUFFER_SIZE),
                                true))
                        .doFinally(signal -> StorageIo.deleteQuietly(merged)));
    }

    private Mono<StorageFile> activate(StoragePolicy policy, StorageDriver driver, StorageFile file, StorageObject object) {
        var spec = file.getSpec();
        if (policy.getSpec().getMaxSize() > 0 && object.size() > policy.getSpec().getMaxSize()) {
            return deleteObject(driver, file)
                    .then(Mono.error(new ApiException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "storage-file-too-large",
                            "文件大小超过策略上限 " + policy.getSpec().getMaxSize() + " 字节。")));
        }
        if (spec.getSize() > 0 && object.size() != spec.getSize()) {
            return deleteObject(driver, file)
                    .then(Mono.error(new ApiException(
                            HttpStatus.BAD_REQUEST, "storage-size-mismatch", "文件大小与声明不一致。")));
        }
        spec.setState(StorageFileState.ACTIVE);
        spec.setSize(object.size());
        spec.setUploadedAt(Instant.now());
        spec.setNativeUploadId(null);
        spec.setUploadExpiresAt(null);
        spec.setPartCount(0);
        spec.setChunkSize(0);
        return Mono.fromRunnable(() -> deleteSessionDirectory(file.getMetadata().getName()))
                .subscribeOn(Schedulers.boundedElastic())
                .then(fileRecords.update(file));
    }

    private Mono<Void> deleteObject(StorageDriver driver, StorageFile file) {
        return driver.delete(List.of(file.getSpec().getStorageKey())).onErrorResume(error -> Mono.empty());
    }

    private Mono<UploadedPart> writeLocalPart(String fileId, int partNumber, Flux<DataBuffer> body) {
        return Mono.fromCallable(() -> {
                    var directory = sessionDirectory(fileId);
                    Files.createDirectories(directory);
                    return directory.resolve("part-" + partNumber);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> DataBufferUtils.write(
                                body,
                                path,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)
                        .then(Mono.fromCallable(() -> new UploadedPart(partNumber, "", Files.size(path))))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    private static Flux<DataBuffer> limit(Flux<DataBuffer> body, long maxBytes) {
        if (maxBytes <= 0) {
            return body;
        }
        var written = new AtomicLong();
        return body.concatMap(buffer -> {
            var total = written.addAndGet(buffer.readableByteCount());
            if (total > maxBytes) {
                DataBufferUtils.release(buffer);
                return Flux.error(partTooLarge());
            }
            return Flux.just(buffer);
        });
    }

    private Mono<List<UploadedPart>> uploadedParts(StorageFile file, StorageDriver driver) {
        if (driver instanceof MultipartStorageDriver multipart && hasNativeUpload(file)) {
            return multipart.listParts(nativeUpload(file)).onErrorResume(error -> Mono.just(List.of()));
        }
        return scanLocalParts(file.getMetadata().getName());
    }

    private Mono<List<UploadedPart>> scanLocalParts(String fileId) {
        return Mono.fromCallable(() -> {
                    var directory = sessionDirectory(fileId);
                    if (Files.notExists(directory)) {
                        return List.<UploadedPart>of();
                    }
                    try (var paths = Files.list(directory)) {
                        return paths.filter(Files::isRegularFile)
                                .map(path -> toPart(path))
                                .filter(part -> part != null)
                                .sorted(Comparator.comparingInt(UploadedPart::partNumber))
                                .toList();
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private UploadedPart toPart(Path path) {
        var name = path.getFileName().toString();
        if (!name.startsWith("part-")) {
            return null;
        }
        try {
            var partNumber = Integer.parseInt(name.substring("part-".length()));
            return new UploadedPart(partNumber, "", Files.size(path));
        } catch (NumberFormatException | IOException e) {
            return null;
        }
    }

    private Mono<Path> mergeLocalParts(StorageFile file) {
        return Mono.fromCallable(() -> {
                    var directory = sessionDirectory(file.getMetadata().getName());
                    var partCount = file.getSpec().getPartCount();
                    var merged = Files.createTempFile(properties.getWorkDir(), ".merge-", ".tmp");
                    try (var output = Files.newOutputStream(
                            merged,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                        for (var part = 1; part <= partCount; part++) {
                            var path = directory.resolve("part-" + part);
                            if (Files.notExists(path)) {
                                throw new ApiException(
                                        HttpStatus.BAD_REQUEST, "storage-part-missing", "缺少分片 " + part + "。");
                            }
                            Files.copy(path, output);
                        }
                    } catch (RuntimeException | IOException e) {
                        StorageIo.deleteQuietly(merged);
                        throw e;
                    }
                    return merged;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void validateParts(StorageFile file, List<UploadedPart> parts) {
        var expected = file.getSpec().getPartCount();
        if (parts == null || parts.size() != expected) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-parts-incomplete", "分片数量不完整。");
        }
        var numbers = new HashSet<Integer>();
        for (var part : parts) {
            numbers.add(part.partNumber());
        }
        for (var part = 1; part <= expected; part++) {
            if (!numbers.contains(part)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "storage-parts-incomplete", "缺少分片 " + part + "。");
            }
        }
    }

    private Mono<Void> abortDriver(StorageFile file, StorageDriver driver) {
        if (driver instanceof MultipartStorageDriver multipart && hasNativeUpload(file)) {
            return multipart.abortMultipart(nativeUpload(file));
        }
        if (driver instanceof DirectUploadDriver) {
            return driver.delete(List.of(file.getSpec().getStorageKey()))
                    .onErrorResume(error -> error instanceof ApiException api
                                    && api.getStatusCode().value() == HttpStatus.NOT_FOUND.value()
                            ? Mono.empty()
                            : Mono.error(error));
        }
        return Mono.empty();
    }

    private Mono<StoragePolicy> resolvePolicy(String policyName) {
        return StringUtils.hasText(policyName)
                ? policies.requireReady(policyName)
                : policies.defaultPolicy();
    }

    private Mono<StorageFile> requireUploading(String fileId) {
        return fileRecords.fetch(fileId).switchIfEmpty(Mono.error(uploadNotFound())).flatMap(file -> {
            if (file.getSpec().getState() != StorageFileState.UPLOADING) {
                return Mono.error(uploadStateInvalid());
            }
            var expiresAt = file.getSpec().getUploadExpiresAt();
            if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                return Mono.error(expired());
            }
            return Mono.just(file);
        });
    }

    private static boolean hasNativeUpload(StorageFile file) {
        return StringUtils.hasText(file.getSpec().getNativeUploadId());
    }

    private static NativeMultipartUpload nativeUpload(StorageFile file) {
        return new NativeMultipartUpload(
                file.getSpec().getNativeUploadId(),
                file.getSpec().getStorageKey(),
                file.getSpec().getChunkSize());
    }

    private static boolean presigned(StorageDriver driver) {
        return driver instanceof MultipartStorageDriver
                && driver.capabilities().contains(StorageCapability.PRESIGNED_PARTS);
    }

    private Map<String, String> copyAttributes(Map<String, String> attributes) {
        if (CollectionUtils.isEmpty(attributes)) {
            return new LinkedHashMap<>();
        }
        var total = 0;
        for (var entry : attributes.entrySet()) {
            // 请求体允许附加信息为 null，先拦掉避免取长度时空指针。
            if (entry.getKey() == null || entry.getValue() == null) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST, "storage-attribute-invalid", "文件附加信息无效。");
            }
            total += entry.getKey().length() + entry.getValue().length();
        }
        if (attributes.size() > MAX_ATTRIBUTES || total > MAX_ATTRIBUTE_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "storage-attributes-too-large", "文件附加信息过大。");
        }
        return new LinkedHashMap<>(attributes);
    }

    private Path sessionDirectory(String fileId) {
        return properties.getWorkDir().toAbsolutePath().normalize().resolve(SESSION_DIRECTORY).resolve(fileId);
    }

    private void createSessionDirectory(String fileId) {
        try {
            Files.createDirectories(sessionDirectory(fileId));
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传会话目录", e);
        }
    }

    private void deleteSessionDirectory(String fileId) {
        var directory = sessionDirectory(fileId);
        if (Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(StorageIo::deleteQuietly);
        } catch (IOException e) {
            log.warn("无法清理上传会话目录 [{}]", directory, e);
        }
    }

    private static ApiException uploadNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "storage-upload-not-found", "上传会话不存在。");
    }

    private static ApiException uploadStateInvalid() {
        return new ApiException(HttpStatus.CONFLICT, "storage-upload-state-invalid", "上传会话状态无效。");
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "storage-upload-expired", "上传会话已过期。");
    }

    private static ApiException partInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "storage-part-invalid", "分片编号无效。");
    }

    private static ApiException partTooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "storage-part-too-large", "分片大小超过上限。");
    }
}
