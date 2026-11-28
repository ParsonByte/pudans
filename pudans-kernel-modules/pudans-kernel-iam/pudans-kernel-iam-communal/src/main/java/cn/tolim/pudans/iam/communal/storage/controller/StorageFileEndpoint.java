package cn.tolim.pudans.iam.communal.storage.controller;

import cn.tolim.pudans.iam.communal.storage.Storage;
import cn.tolim.pudans.iam.communal.storage.StorageContent;
import cn.tolim.pudans.iam.infra.constant.IamConstants;
import cn.tolim.pudans.iam.infra.result.ApiRestResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.MultipartConfigElement;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.MimeTypeUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import static cn.tolim.pudans.iam.communal.enums.ViewContentType.SVG;
import static cn.tolim.pudans.iam.communal.storage.controller.StorageFileEndpoint.STORAGE_PATH;


@Validated
@Tag(name = "存储文件")
@RestController
@RequestMapping(value = STORAGE_PATH)
@AllArgsConstructor
@Slf4j
public class StorageFileEndpoint {

    // Storage API path
    public final static String STORAGE_PATH = IamConstants.V1_API_PATH + "/storage";
    // Storage
    private final Storage storage;
    private final MultipartConfigElement multipartConfigElement;

    // Upload file
    // Returns: ApiRestResult
    @Operation(summary = "上传文件")
    @PostMapping(value = "/upload")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Object> uploadFile(@Schema(description = "文件") MultipartFile file) {
        try {
            if (storage == null) {
                return ApiRestResult.err().message("暂未开启存储服务，请联系管理员");
            }
            String fileName = new String(Objects.requireNonNull(file.getOriginalFilename())
                    .getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
            String fileType = file.getContentType();
            List<String> allowedTypes = Arrays.asList(MimeTypeUtils.IMAGE_JPEG_VALUE,
                    MimeTypeUtils.IMAGE_PNG_VALUE, SVG.getType(), "image/vnd.microsoft.icon");
            if (Objects.isNull(fileType) || !allowedTypes.contains(fileType)) {
                return ApiRestResult.err().message("文件类型不支持，请上传图片");
            }
            return ApiRestResult.ok(storage.upload(fileName, file.getInputStream()));
        } catch (Exception e) {
            log.error("上传存储文件失败：{}", e.getMessage(), e);
            return ApiRestResult.err().message(e.getMessage());
        }
    }

    // Get stored file path
    // Returns: ApiRestResult
    @Operation(summary = "获取存储文件路径")
    @GetMapping(value = "/get")
    @PreAuthorize(value = "authenticated")
    public ApiRestResult<Object> getUrl(@Schema(description = "文件路径") @RequestParam String path) {
        try {
            if (storage == null) {
                return ApiRestResult.err().message("暂未开启存储服务，请联系管理员");
            }
            return ApiRestResult.ok(storage.download(path));
        } catch (Exception e) {
            log.error("获取存储文件失败：{}", e.getMessage(), e);
            return ApiRestResult.err().message(e.getMessage());
        }
    }

    // Stream stored file content through the IAM service
    // Returns: ResponseEntity<Resource>
    @Operation(summary = "获取存储文件内容")
    @GetMapping(value = "/content")
    @PreAuthorize(value = "authenticated")
    public ResponseEntity<Resource> content(@RequestParam String path) {
        try {
            if (storage == null) {
                return ResponseEntity.notFound().build();
            }
            StorageContent content = storage.open(path);
            MediaType mediaType = resolveMediaType(content.contentType());
            ContentDisposition.Builder disposition = isInline(mediaType)
                    ? ContentDisposition.inline() : ContentDisposition.attachment();
            if (content.fileName() != null && !content.fileName().isBlank()) {
                disposition.filename(content.fileName(), StandardCharsets.UTF_8);
            }
            ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.build().toString())
                    .header("X-Content-Type-Options", "nosniff");
            if (content.size() >= 0) {
                builder.contentLength(content.size());
            }
            return builder.body(new InputStreamResource(content.content()));
        } catch (Exception e) {
            log.error("获取存储文件内容失败：{}", e.getMessage(), e);
            return ResponseEntity.notFound().build();
        }
    }

    // Uploaded file too large
    // Returns: ApiRestResult<String>
    @ExceptionHandler(value = {MaxUploadSizeExceededException.class})
    public ApiRestResult<Object> maxUploadSizeExceededException() {
        return ApiRestResult.err()
                .message("文件大小不能超过:" + multipartConfigElement.getMaxFileSize() / 1024 / 1024 + "M");
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