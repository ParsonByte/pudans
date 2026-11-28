package cn.tolim.pudans.repository.service;

import cn.tolim.pudans.repository.contract.DownloadUrlResponse;
import cn.tolim.pudans.repository.contract.FileMetadataResponse;
import cn.tolim.pudans.repository.contract.FilePageResponse;
import cn.tolim.pudans.repository.contract.RepositoryApiPaths;
import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.entity.FileStatus;
import cn.tolim.pudans.repository.entity.RepositoryFileEntity;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.pudans.repository.repository.RepositoryFileRepository;
import cn.tolim.pudans.repository.storage.ObjectStorage;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class FileStorageService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int MAX_NAME_LENGTH = 200;
    private static final int MAX_SEGMENT_LENGTH = 64;

    private final RepositoryFileRepository repository;
    private final ObjectStorageRegistry registry;
    private final ChecksumService checksumService;
    private final RepositoryProperties properties;

    public FileStorageService(RepositoryFileRepository repository, ObjectStorageRegistry registry,
                              ChecksumService checksumService, RepositoryProperties properties) {
        this.repository = repository;
        this.registry = registry;
        this.checksumService = checksumService;
        this.properties = properties;
    }

    @Transactional
    public FileMetadataResponse upload(FileUploadCommand command) {
        if (command == null || command.content() == null) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    "Upload content is required");
        }
        long size = command.size();
        if (size < 0) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    "Upload size must not be negative");
        }
        if (size > properties.getMaxFileSize()) {
            throw new RepositoryException(RepositoryErrorCode.FILE_TOO_LARGE,
                    "File exceeds the maximum allowed size of " + properties.getMaxFileSize()
                            + " bytes");
        }
        String tenantId = requireLength(resolveTenant(command.tenantId()), 128, "tenantId");
        String appCode = requireLength(trimToNull(command.appCode()), 128, "appCode");
        String category = requireLength(trimToNull(command.category()), 128, "category");
        String originalName = sanitizeFileName(command.originalName());
        String contentType = sanitizeContentType(command.contentType());
        validateContentType(contentType);
        String createdBy = trimToNull(command.createdBy());
        if (createdBy == null) {
            createdBy = properties.getDefaultCreatedBy();
        }
        createdBy = requireLength(createdBy, 128, "createdBy");
        StorageProviderType provider = command.provider() == null
                ? registry.defaultProvider() : command.provider();
        ObjectStorage storage = registry.require(provider);
        Map<String, String> metadata = sanitizeMetadata(command.metadata());
        String extension = extensionOf(originalName);
        String objectKey = buildObjectKey(provider, tenantId, appCode, category, extension);
        String checksum = checksumService.sha256(command.content(), size);

        StoredObject stored;
        try (InputStream input = command.content().open()) {
            stored = storage.put(objectKey, input, size, contentType, metadata);
        } catch (IOException exception) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    "Upload content could not be read", exception);
        }

        RepositoryFileEntity entity = new RepositoryFileEntity();
        entity.setId(UUID.randomUUID().toString().replace("-", ""));
        entity.setTenantId(tenantId);
        entity.setAppCode(appCode);
        entity.setCategory(category);
        entity.setProvider(provider);
        entity.setBucket(bucketOf(provider));
        entity.setObjectKey(stored.objectKey() == null ? objectKey : stored.objectKey());
        entity.setOriginalName(originalName);
        entity.setContentType(contentType);
        entity.setExtension(extension);
        entity.setSize(stored.size() >= 0 ? stored.size() : size);
        entity.setChecksumSha256(checksum);
        entity.setStatus(FileStatus.ACTIVE);
        entity.setMetadata(metadata);
        entity.setCreateBy(createdBy);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        try {
            repository.save(entity);
        } catch (RuntimeException exception) {
            deleteQuietly(storage, objectKey);
            throw exception;
        }
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public FileMetadataResponse metadata(String fileId, String tenantId) {
        return toResponse(requireFile(fileId, tenantId));
    }

    @Transactional(readOnly = true)
    public FilePageResponse list(FileQuery query) {
        int page = query == null ? 0 : Math.max(query.page(), 0);
        int size = query == null ? 20 : query.size();
        size = Math.min(Math.max(size, 1), properties.getMaxPageSize());
        String tenantId = resolveTenant(query == null ? null : query.tenantId());
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createTime"));
        Page<RepositoryFileEntity> result = repository.search(tenantId,
                query == null ? null : trimToNull(query.appCode()),
                query == null ? null : trimToNull(query.category()),
                query == null ? null : trimToNull(query.keyword()), pageable);
        List<FileMetadataResponse> items = result.getContent().stream()
                .map(this::toResponse)
                .toList();
        return new FilePageResponse(items, result.getTotalElements(), result.getNumber(),
                result.getSize(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public FileContent download(String fileId, String tenantId) {
        RepositoryFileEntity entity = requireFile(fileId, tenantId);
        ObjectStorage storage = registry.require(entity.getProvider());
        InputStream content = storage.open(entity.getObjectKey());
        return new FileContent(entity, content);
    }

    @Transactional(readOnly = true)
    public DownloadUrlResponse downloadUrl(String fileId, String tenantId) {
        RepositoryFileEntity entity = requireFile(fileId, tenantId);
        ObjectStorage storage = registry.require(entity.getProvider());
        if (storage.directDownload()) {
            URI uri = storage.presignedGetUrl(entity.getObjectKey(),
                    properties.getPresignedUrlTtl());
            return new DownloadUrlResponse(uri.toString(),
                    LocalDateTime.now().plus(properties.getPresignedUrlTtl()),
                    entity.getProvider().code(), true);
        }
        return new DownloadUrlResponse(RepositoryApiPaths.fileContentPath(entity.getId()), null,
                entity.getProvider().code(), false);
    }

    @Transactional
    public void delete(String fileId, String tenantId) {
        RepositoryFileEntity entity = requireFile(fileId, tenantId);
        ObjectStorage storage = registry.require(entity.getProvider());
        try {
            storage.delete(entity.getObjectKey());
        } catch (StorageObjectNotFoundException ignored) {
            // The object is already gone; the metadata still has to be marked deleted
        }
        entity.setStatus(FileStatus.DELETED);
        entity.setDeleteTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        repository.save(entity);
    }

    public RepositoryFileEntity requireFile(String fileId, String tenantId) {
        if (fileId == null || fileId.isBlank()) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    "File identifier is required");
        }
        String resolvedTenant = resolveTenant(tenantId);
        return repository.findByIdAndTenantIdAndDeleteTimeIsNull(fileId.trim(), resolvedTenant)
                .orElseThrow(() -> new RepositoryException(RepositoryErrorCode.FILE_NOT_FOUND,
                        "File not found: " + fileId));
    }

    public String resolveTenant(String tenantId) {
        String resolved = trimToNull(tenantId);
        if (resolved == null) {
            resolved = properties.getDefaultTenantId();
        }
        if (resolved == null || resolved.isBlank()) {
            resolved = "default";
        }
        return resolved;
    }

    public FileMetadataResponse toResponse(RepositoryFileEntity entity) {
        return new FileMetadataResponse(entity.getId(), entity.getTenantId(),
                entity.getAppCode(), entity.getCategory(),
                entity.getProvider() == null ? null : entity.getProvider().code(),
                entity.getObjectKey(), entity.getOriginalName(), entity.getContentType(),
                entity.getExtension(), entity.getSize(), entity.getChecksumSha256(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getCreateBy(), entity.getCreateTime(), entity.getUpdateTime(),
                entity.getMetadata() == null ? Map.of() : entity.getMetadata());
    }

    private String buildObjectKey(StorageProviderType provider, String tenantId, String appCode,
                                  String category, String extension) {
        StringBuilder builder = new StringBuilder();
        String prefix = normalizePrefix(locationOf(provider));
        if (!prefix.isEmpty()) {
            builder.append(prefix).append('/');
        }
        builder.append(safeSegment(tenantId)).append('/');
        if (appCode != null) {
            builder.append(safeSegment(appCode)).append('/');
        }
        if (category != null) {
            builder.append(safeSegment(category)).append('/');
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        builder.append(today.getYear()).append('/')
                .append(String.format(Locale.ROOT, "%02d", today.getMonthValue())).append('/')
                .append(UUID.randomUUID().toString().replace("-", ""));
        if (!extension.isEmpty()) {
            builder.append('.').append(extension);
        }
        return builder.toString();
    }

    private String locationOf(StorageProviderType provider) {
        return switch (provider) {
            case LOCAL -> "";
            case ALIYUN_OSS -> properties.getAliyunOss().getLocation();
            case TENCENT_COS -> properties.getTencentCos().getLocation();
            case MINIO -> properties.getMinio().getLocation();
            case QINIU_KODO -> properties.getQiniu().getLocation();
            case S3 -> properties.getS3().getLocation();
        };
    }

    private String bucketOf(StorageProviderType provider) {
        return switch (provider) {
            case LOCAL -> null;
            case ALIYUN_OSS -> properties.getAliyunOss().getBucket();
            case TENCENT_COS -> properties.getTencentCos().getBucket();
            case MINIO -> properties.getMinio().getBucket();
            case QINIU_KODO -> properties.getQiniu().getBucket();
            case S3 -> properties.getS3().getBucket();
        };
    }

    private void validateContentType(String contentType) {
        if (properties.getAllowedContentTypes() == null
                || properties.getAllowedContentTypes().isEmpty()) {
            return;
        }
        boolean matched = properties.getAllowedContentTypes().stream()
                .filter(pattern -> pattern != null && !pattern.isBlank())
                .anyMatch(pattern -> matchesContentType(pattern.trim(), contentType));
        if (!matched) {
            throw new RepositoryException(RepositoryErrorCode.CONTENT_TYPE_NOT_ALLOWED,
                    "Content type is not allowed: " + contentType);
        }
    }

    private static boolean matchesContentType(String pattern, String contentType) {
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return contentType.toLowerCase(Locale.ROOT)
                    .startsWith(prefix.toLowerCase(Locale.ROOT));
        }
        return pattern.equalsIgnoreCase(contentType);
    }

    private static String sanitizeContentType(String contentType) {
        String value = trimToNull(contentType);
        if (value == null || value.contains("\r") || value.contains("\n")) {
            return DEFAULT_CONTENT_TYPE;
        }
        return requireLength(value, 255, "contentType");
    }

    private static String requireLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    field + " must not exceed " + maxLength + " characters");
        }
        return value;
    }

    private static Map<String, String> sanitizeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, String> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                sanitized.put(key.trim(), value);
            }
        });
        return Map.copyOf(sanitized);
    }

    private static String sanitizeFileName(String originalName) {
        String value = originalName == null ? "" : originalName.replace('\\', '/');
        int separator = value.lastIndexOf('/');
        if (separator >= 0) {
            value = value.substring(separator + 1);
        }
        value = value.replaceAll("\\p{Cntrl}", "").trim();
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            value = "file";
        }
        if (value.length() > MAX_NAME_LENGTH) {
            String extension = extensionOf(value);
            int keep = extension.isEmpty() ? MAX_NAME_LENGTH
                    : MAX_NAME_LENGTH - extension.length() - 1;
            value = value.substring(0, Math.max(keep, 1))
                    + (extension.isEmpty() ? "" : "." + extension);
        }
        return value;
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) {
            return "";
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0 || dot == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,16}") ? extension : "";
    }

    private static String safeSegment(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9._-]", "-");
        normalized = normalized.replaceAll("\\.{2,}", "-");
        normalized = normalized.replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^[._-]+", "");
        normalized = normalized.replaceAll("[._-]+$", "");
        if (normalized.length() > MAX_SEGMENT_LENGTH) {
            normalized = normalized.substring(0, MAX_SEGMENT_LENGTH);
        }
        return normalized.isEmpty() ? "default" : normalized;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        String normalized = prefix.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains("..") || normalized.contains("\u0000")) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_OBJECT_KEY,
                    "Storage key prefix is invalid");
        }
        return normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void deleteQuietly(ObjectStorage storage, String objectKey) {
        try {
            storage.delete(objectKey);
        } catch (RuntimeException ignored) {
            // Best effort cleanup after a failed metadata save
        }
    }
}