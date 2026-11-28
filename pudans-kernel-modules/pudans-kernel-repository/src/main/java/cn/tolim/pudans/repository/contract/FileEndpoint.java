package cn.tolim.pudans.repository.contract;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.pudans.repository.service.FileContent;
import cn.tolim.pudans.repository.service.FileQuery;
import cn.tolim.pudans.repository.service.FileStorageService;
import cn.tolim.pudans.repository.service.FileUploadCommand;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@RestController
@Tag(name = "文件仓储")
@RequestMapping(value = RepositoryApiPaths.FILES, produces = MediaType.APPLICATION_JSON_VALUE)
public class FileEndpoint {

    private final FileStorageService fileStorageService;
    private final ObjectStorageRegistry storageRegistry;

    public FileEndpoint(FileStorageService fileStorageService,
                        ObjectStorageRegistry storageRegistry) {
        this.fileStorageService = fileStorageService;
        this.storageRegistry = storageRegistry;
    }

    @Operation(summary = "上传文件")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FileMetadataResponse> upload(@ModelAttribute FileUploadForm form) {
        MultipartFile file = form.getFile();
        if (file == null) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    "Multipart file part 'file' is required");
        }
        FileUploadCommand command = new FileUploadCommand(form.getTenantId(), form.getAppCode(),
                form.getCategory(), file.getOriginalFilename(), file.getContentType(),
                file.getSize(), form.getCreatedBy(), form.getMetadata(),
                parseProvider(form.getProvider()), file::getInputStream);
        return ApiResponse.ok(fileStorageService.upload(command));
    }

    @Operation(summary = "查询文件列表")
    @GetMapping
    public ApiResponse<FilePageResponse> list(
            @RequestParam(name = "tenantId", required = false) String tenantId,
            @RequestParam(name = "appCode", required = false) String appCode,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        FileQuery query = new FileQuery(tenantId, appCode, category, keyword, page, size);
        return ApiResponse.ok(fileStorageService.list(query));
    }

    @Operation(summary = "查询存储提供方")
    @GetMapping("/providers")
    public ApiResponse<List<StorageProviderResponse>> providers() {
        StorageProviderType defaultProvider = storageRegistry.defaultProvider();
        List<StorageProviderResponse> providers = Arrays.stream(StorageProviderType.values())
                .map(provider -> new StorageProviderResponse(provider.code(),
                        storageRegistry.isConfigured(provider), provider == defaultProvider))
                .toList();
        return ApiResponse.ok(providers);
    }

    @Operation(summary = "查询文件详情")
    @GetMapping("/{id}")
    public ApiResponse<FileMetadataResponse> metadata(@PathVariable("id") String id,
                                                      @RequestParam(name = "tenantId",
                                                              required = false) String tenantId) {
        return ApiResponse.ok(fileStorageService.metadata(id, tenantId));
    }

    @Operation(summary = "获取文件内容")
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable("id") String id,
                                            @RequestParam(name = "tenantId",
                                                    required = false) String tenantId) {
        FileContent file = fileStorageService.download(id, tenantId);
        MediaType mediaType = resolveMediaType(file.file().getContentType());
        InputStreamResource resource = new InputStreamResource(file.content());
        ContentDisposition.Builder disposition = isInline(mediaType)
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(file.file().getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition
                        .filename(file.file().getOriginalName(), StandardCharsets.UTF_8)
                        .build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    @Operation(summary = "获取文件下载地址")
    @GetMapping("/{id}/url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(@PathVariable("id") String id,
                                                        @RequestParam(name = "tenantId",
                                                                required = false) String tenantId) {
        return ApiResponse.ok(fileStorageService.downloadUrl(id, tenantId));
    }

    @Operation(summary = "删除文件")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") String id,
                                    @RequestParam(name = "tenantId", required = false) String tenantId) {
        fileStorageService.delete(id, tenantId);
        return ApiResponse.ok();
    }

    private static StorageProviderType parseProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return null;
        }
        try {
            return StorageProviderType.fromCode(provider);
        } catch (IllegalArgumentException exception) {
            throw new RepositoryException(RepositoryErrorCode.INVALID_REQUEST,
                    exception.getMessage());
        }
    }

    private static MediaType resolveMediaType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(contentType.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    // Images are served inline so they can be used directly as page assets
    private static boolean isInline(MediaType mediaType) {
        return "image".equalsIgnoreCase(mediaType.getType());
    }
}