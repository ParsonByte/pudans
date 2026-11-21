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
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
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

    // 按请求策略创建上传会话或直传凭证。
    public Mono<UploadTicket> initiate(StorageUploadRequest request) {
        return resolvePolicy(request.policy())
                .flatMap(policy -> parameterGroups
                        .getStorage()
                        .flatMap(settings -> createUpload(policy, request, settings)));
    }

    // 校验上传令牌与文件标识是否匹配。
    public void verifyUploadToken(String token, String fileId) {
        transferTokens.verifyUpload(token, fileId);
    }

    // 为仍有效的上传会话续签上传令牌。
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

    // 汇总上传进度、分片信息与预签名能力。
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

    // 将分片写入原生多段上传或本地会话。
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
                    return multipart.putPart(
                            nativeUpload(file), partNumber, StorageIo.limit(body, maxPartSize), contentLength);
                }
                return writeLocalPart(fileId, partNumber, StorageIo.limit(body, maxPartSize), maxPartSize);
            });
        });
    }

    // 为支持预签名的驱动生成分片地址。
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

    // 校验分片完整性并激活上传完成的文件。
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

    // 终止上传并清理驱动会话与本地分片。
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

    // 一次性写入内容并创建可读文件记录。
    public Mono<StorageFile> upload(StorageWriteRequest request) {
        return resolvePolicy(request.policy())
                .flatMap(policy -> parameterGroups
                        .getStorage()
                        .flatMap(settings -> createWrite(policy, request, settings)));
    }

    // 校验请求后直接写入对象存储并落库。
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
                                    .flatMap(driver -> {
                                        var maxBytes = request.size() > 0
                                                ? request.size()
                                                : policy.getSpec().getMaxSize();
                                        return putFile(driver, created, request, chunkSize, partCount, maxBytes)
                                                .flatMap(object -> activate(policy, driver, created, object)
                                                        .onErrorResume(error -> deleteObject(driver, created)
                                                                .then(Mono.error(error))));
                                    })
                                    .onErrorResume(error -> fileRecords.delete(created).then(Mono.error(error))));
                });
    }

    // 按大小与驱动能力选择普通写入或多段上传。
    private Mono<StorageObject> putFile(
            StorageDriver driver,
            StorageFile file,
            StorageWriteRequest request,
            long chunkSize,
            int partCount,
            long maxBytes) {
        var spec = file.getSpec();
        if (request.size() > chunkSize
                && driver instanceof MultipartStorageDriver multipart
                && driver.capabilities().contains(StorageCapability.MULTIPART_UPLOAD)) {
            return uploadMultipartBody(multipart, file, request, chunkSize, partCount, maxBytes);
        }
        return driver.put(new StoragePutRequest(
                spec.getStorageKey(),
                spec.getSize(),
                spec.getContentType(),
                StorageIo.limit(request.body(), maxBytes),
                request.overwrite()));
    }

    // 先落盘再分片上传，完成后清理临时文件。
    private Mono<StorageObject> uploadMultipartBody(
            MultipartStorageDriver multipart,
            StorageFile file,
            StorageWriteRequest request,
            long chunkSize,
            int partCount,
            long maxBytes) {
        return StorageIo.spool(request.body(), tempDirectory(), maxBytes)
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

    // 顺序上传全部分片，避免并发占用内存。
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

    // 从文件指定偏移读取指定长度。
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

    // 返回上传流程使用的临时目录。
    private Path tempDirectory() {
        return properties.getWorkDir().toAbsolutePath().normalize().resolve("uploads/.tmp");
    }

    // 清理超过保留时间的临时文件与无记录引用的上传会话目录。
    public Mono<Void> cleanupLocalArtifacts(Set<String> activeUploads, Instant cutoff) {
        return Mono.fromRunnable(() -> {
                    var workDir = properties.getWorkDir().toAbsolutePath().normalize();
                    cleanupStaleFiles(tempDirectory(), cutoff, name -> true);
                    cleanupStaleFiles(workDir, cutoff, name -> name.startsWith(".merge-"));
                    cleanupStaleSessions(workDir.resolve(SESSION_DIRECTORY), activeUploads, cutoff);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // 删除目录中超过保留时间且符合名称条件的普通文件。
    private static void cleanupStaleFiles(Path directory, Instant cutoff, Predicate<String> filter) {
        if (Files.notExists(directory)) {
            return;
        }
        try (var paths = Files.list(directory)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> filter.test(path.getFileName().toString()))
                    .filter(path -> modifiedBefore(path, cutoff))
                    .forEach(StorageIo::deleteQuietly);
        } catch (IOException e) {
            log.warn("清理存储临时文件失败 [{}]", directory, e);
        }
    }

    // 删除没有上传记录引用且已过保留时间的会话目录。
    private void cleanupStaleSessions(Path sessionsRoot, Set<String> activeUploads, Instant cutoff) {
        if (Files.notExists(sessionsRoot)) {
            return;
        }
        try (var paths = Files.list(sessionsRoot)) {
            paths.filter(Files::isDirectory)
                    .filter(path -> !activeUploads.contains(path.getFileName().toString()))
                    .filter(path -> modifiedBefore(path, cutoff))
                    .forEach(path -> deleteSessionDirectory(path.getFileName().toString()));
        } catch (IOException e) {
            log.warn("清理上传会话目录失败 [{}]", sessionsRoot, e);
        }
    }

    // 判断路径的最后修改时间是否早于截止时间。
    private static boolean modifiedBefore(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path).toInstant().isBefore(cutoff);
        } catch (IOException e) {
            return false;
        }
    }

    // 创建上传记录并初始化驱动侧上传会话。
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

    // 构造处于上传状态的文件记录。
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

    // 按驱动能力初始化直传、多段或本地会话。
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
                        return fileRecords
                                .update(file)
                                .onErrorResume(error -> multipart
                                        .abortMultipart(multipartUpload)
                                        .onErrorResume(abortError -> Mono.empty())
                                        .then(Mono.error(error)));
                    })
                    .map(saved -> ticket(saved, driver, expiresAt, null))
                    .onErrorResume(error -> fileRecords.delete(file).then(Mono.error(error)));
        }
        return Mono.fromRunnable(() -> createSessionDirectory(fileId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ticket(file, driver, expiresAt, null));
    }

    // 组装上传凭证并签发上传令牌。
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

    // 完成多段上传或合并本地分片后返回对象。
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

    // 校验对象大小并标记文件可读，随后清理会话。
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

    // 尽力删除已写入对象，失败不覆盖主错误。
    private Mono<Void> deleteObject(StorageDriver driver, StorageFile file) {
        return driver.delete(List.of(file.getSpec().getStorageKey())).onErrorResume(error -> Mono.empty());
    }

    // 将分片写入本地上传会话目录。
    private Mono<UploadedPart> writeLocalPart(String fileId, int partNumber, Flux<DataBuffer> body, long maxBytes) {
        return Mono.fromCallable(() -> {
                    var directory = sessionDirectory(fileId);
                    StorageIo.ensureUsableSpace(directory, maxBytes);
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

    // 查询驱动或本地目录中已上传的分片。
    private Mono<List<UploadedPart>> uploadedParts(StorageFile file, StorageDriver driver) {
        if (driver instanceof MultipartStorageDriver multipart && hasNativeUpload(file)) {
            return multipart.listParts(nativeUpload(file)).onErrorResume(error -> Mono.just(List.of()));
        }
        return scanLocalParts(file.getMetadata().getName());
    }

    // 扫描本地会话目录并还原分片列表。
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

    // 将本地分片文件解析为分片信息。
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

    // 按序合并本地分片为单个临时文件。
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

    // 校验分片数量与编号是否连续完整。
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

    // 按驱动类型清理未完成的上传会话。
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

    // 使用请求策略，未指定时回退到默认策略。
    private Mono<StoragePolicy> resolvePolicy(String policyName) {
        return StringUtils.hasText(policyName)
                ? policies.requireReady(policyName)
                : policies.defaultPolicy();
    }

    // 读取上传记录并校验状态与有效期。
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

    // 判断文件是否绑定了驱动侧多段上传。
    private static boolean hasNativeUpload(StorageFile file) {
        return StringUtils.hasText(file.getSpec().getNativeUploadId());
    }

    // 构造驱动侧多段上传的上下文。
    private static NativeMultipartUpload nativeUpload(StorageFile file) {
        return new NativeMultipartUpload(
                file.getSpec().getNativeUploadId(),
                file.getSpec().getStorageKey(),
                file.getSpec().getChunkSize());
    }

    // 判断驱动是否支持预签名分片。
    private static boolean presigned(StorageDriver driver) {
        return driver instanceof MultipartStorageDriver
                && driver.capabilities().contains(StorageCapability.PRESIGNED_PARTS);
    }

    // 复制附加信息并校验数量与总长度。
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

    // 返回指定上传会话的本地目录。
    private Path sessionDirectory(String fileId) {
        return properties.getWorkDir().toAbsolutePath().normalize().resolve(SESSION_DIRECTORY).resolve(fileId);
    }

    // 创建本地上传会话目录。
    private void createSessionDirectory(String fileId) {
        try {
            Files.createDirectories(sessionDirectory(fileId));
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传会话目录", e);
        }
    }

    // 递归删除会话目录，失败仅记录日志。
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

    // 构造上传会话不存在错误。
    private static ApiException uploadNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "storage-upload-not-found", "上传会话不存在。");
    }

    // 构造上传会话状态无效错误。
    private static ApiException uploadStateInvalid() {
        return new ApiException(HttpStatus.CONFLICT, "storage-upload-state-invalid", "上传会话状态无效。");
    }

    // 构造上传会话过期错误。
    private static ApiException expired() {
        return new ApiException(HttpStatus.GONE, "storage-upload-expired", "上传会话已过期。");
    }

    // 构造分片编号无效错误。
    private static ApiException partInvalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, "storage-part-invalid", "分片编号无效。");
    }

    // 构造分片大小超限错误。
    private static ApiException partTooLarge() {
        return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "storage-part-too-large", "分片大小超过上限。");
    }
}
