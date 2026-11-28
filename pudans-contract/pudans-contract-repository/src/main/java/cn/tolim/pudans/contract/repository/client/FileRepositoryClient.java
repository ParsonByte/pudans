package cn.tolim.pudans.contract.repository.client;

import cn.tolim.pudans.contract.repository.RepositoryContractConstants;
import cn.tolim.pudans.contract.repository.RepositoryApiPaths;
import cn.tolim.pudans.contract.repository.dto.DownloadUrlResponse;
import cn.tolim.pudans.contract.repository.dto.FileMetadataResponse;
import cn.tolim.pudans.contract.repository.dto.FilePageResponse;
import cn.tolim.pudans.contract.repository.dto.RepositoryApiResult;
import cn.tolim.pudans.contract.repository.dto.StorageProviderResponse;
import feign.Response;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

// File repository service contract
@FeignClient(name = RepositoryContractConstants.SERVICE_NAME, contextId = "fileRepositoryClient",
        path = RepositoryApiPaths.BASE)
public interface FileRepositoryClient {

    // Upload a file
    // Parameter file: MultipartFile
    // Parameter tenantId: String
    // Parameter appCode: String
    // Parameter category: String
    // Parameter provider: String
    // Parameter createdBy: String
    // Returns: RepositoryApiResult<FileMetadataResponse>
    @PostMapping(value = RepositoryApiPaths.FILES_SUFFIX,
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    RepositoryApiResult<FileMetadataResponse> uploadFile(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "appCode", required = false) String appCode,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "provider", required = false) String provider,
            @RequestParam(value = "createdBy", required = false) String createdBy);

    // List files
    // Parameter tenantId: String
    // Parameter appCode: String
    // Parameter category: String
    // Parameter keyword: String
    // Parameter page: Integer
    // Parameter size: Integer
    // Returns: RepositoryApiResult<FilePageResponse>
    @GetMapping(RepositoryApiPaths.FILES_SUFFIX)
    RepositoryApiResult<FilePageResponse> listFiles(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "appCode", required = false) String appCode,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") Integer page,
            @RequestParam(value = "size", defaultValue = "20") Integer size);

    // Get file metadata
    // Parameter id: String
    // Parameter tenantId: String
    // Returns: RepositoryApiResult<FileMetadataResponse>
    @GetMapping(RepositoryApiPaths.FILE_DETAIL_SUFFIX)
    RepositoryApiResult<FileMetadataResponse> getMetadata(@PathVariable("id") String id,
                                                         @RequestParam(value = "tenantId",
                                                                 required = false) String tenantId);

    // Get a file download URL
    // Parameter id: String
    // Parameter tenantId: String
    // Returns: RepositoryApiResult<DownloadUrlResponse>
    @GetMapping(RepositoryApiPaths.FILE_URL_SUFFIX)
    RepositoryApiResult<DownloadUrlResponse> getDownloadUrl(@PathVariable("id") String id,
                                                           @RequestParam(value = "tenantId",
                                                                   required = false) String tenantId);

    // Download file content through the repository service
    // Parameter id: String
    // Parameter tenantId: String
    // Returns: Response
    @GetMapping(RepositoryApiPaths.FILE_CONTENT_SUFFIX)
    Response downloadContent(@PathVariable("id") String id,
                             @RequestParam(value = "tenantId", required = false) String tenantId);

    // Delete one file
    // Parameter id: String
    // Parameter tenantId: String
    // Returns: RepositoryApiResult<Void>
    @DeleteMapping(RepositoryApiPaths.FILE_DETAIL_SUFFIX)
    RepositoryApiResult<Void> deleteFile(@PathVariable("id") String id,
                                         @RequestParam(value = "tenantId", required = false) String tenantId);

    // List storage providers
    // Returns: RepositoryApiResult<List<StorageProviderResponse>>
    @GetMapping(RepositoryApiPaths.PROVIDERS_SUFFIX)
    RepositoryApiResult<List<StorageProviderResponse>> listProviders();
}