package cn.tolim.pudans.repository.contract;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import cn.tolim.pudans.communal.web.GlobalExceptionHandler;
import cn.tolim.pudans.contract.repository.RepositoryApiPaths;
import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.entity.FileStatus;
import cn.tolim.pudans.repository.entity.RepositoryFileEntity;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.pudans.repository.security.InternalTokenAuthenticationFilter;
import cn.tolim.pudans.repository.service.FileContent;
import cn.tolim.pudans.repository.service.FileStorageService;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.local.LocalFileSystemStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies the controller is mounted on the published contract paths and guarded by the internal token
class FileEndpointTests {

    private static final String INTERNAL_TOKEN = "endpoint-test-token";
    private static final String FILE_ID = "file-1";

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        fileStorageService = mock(FileStorageService.class);
        RepositoryProperties.Local local = new RepositoryProperties.Local();
        local.setBaseDirectory(tempDir.resolve("storage").toString());
        ObjectStorageRegistry registry = ObjectStorageRegistry.of(
                Map.of(StorageProviderType.LOCAL, new LocalFileSystemStorage(local)),
                StorageProviderType.LOCAL);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new FileEndpoint(fileStorageService, registry))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new InternalTokenAuthenticationFilter(INTERNAL_TOKEN,
                        JsonMapper.builder().build()))
                .build();
    }

    @Test
    void rejectsRequestsWithoutInternalToken() throws Exception {
        mockMvc.perform(get(RepositoryApiPaths.PROVIDERS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void rejectsBlankInternalTokenHeader() throws Exception {
        mockMvc.perform(get(RepositoryApiPaths.PROVIDERS)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsProvidersOnContractPath() throws Exception {
        mockMvc.perform(get(RepositoryApiPaths.PROVIDERS)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data[0].provider").value("local"))
                .andExpect(jsonPath("$.data[0].configured").value(true))
                .andExpect(jsonPath("$.data[0].defaultProvider").value(true));
    }

    @Test
    void listsFilesOnContractPath() throws Exception {
        when(fileStorageService.list(any()))
                .thenReturn(new FilePageResponse(List.of(metadata()), 1, 0, 20, 1));

        mockMvc.perform(get(RepositoryApiPaths.FILES)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(FILE_ID));
    }

    @Test
    void uploadsOnContractPath() throws Exception {
        when(fileStorageService.upload(any())).thenReturn(metadata());
        MockMultipartFile file = new MockMultipartFile("file", "report.txt", MediaType.TEXT_PLAIN_VALUE,
                "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart(RepositoryApiPaths.FILES)
                        .file(file)
                        .param("tenantId", "default")
                        .param("appCode", "docs")
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(FILE_ID));
    }

    @Test
    void readsMetadataOnContractPath() throws Exception {
        when(fileStorageService.metadata(FILE_ID, null)).thenReturn(metadata());

        mockMvc.perform(get(RepositoryApiPaths.FILE_DETAIL.replace("{id}", FILE_ID))
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("report.txt"));
    }

    @Test
    void streamsContentOnContractPath() throws Exception {
        RepositoryFileEntity entity = entity();
        when(fileStorageService.download(FILE_ID, null))
                .thenReturn(new FileContent(entity, new ByteArrayInputStream(entity.getObjectKey()
                        .getBytes(StandardCharsets.UTF_8))));

        mockMvc.perform(get(RepositoryApiPaths.FILE_CONTENT.replace("{id}", FILE_ID))
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", containsString("report.txt")));
    }

    @Test
    void returnsDownloadUrlOnContractPath() throws Exception {
        when(fileStorageService.downloadUrl(FILE_ID, null)).thenReturn(
                new DownloadUrlResponse("https://files.example.com/report.txt", null, "local", true));

        mockMvc.perform(get(RepositoryApiPaths.FILE_URL.replace("{id}", FILE_ID))
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://files.example.com/report.txt"));
    }

    @Test
    void deletesOnContractPath() throws Exception {
        doNothing().when(fileStorageService).delete(FILE_ID, null);

        mockMvc.perform(delete(RepositoryApiPaths.FILE_DETAIL.replace("{id}", FILE_ID))
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponse.SUCCESS_CODE));
    }

    @Test
    void mapsMissingFileToNotFound() throws Exception {
        when(fileStorageService.metadata(FILE_ID, null))
                .thenThrow(new RepositoryException(RepositoryErrorCode.FILE_NOT_FOUND));

        mockMvc.perform(get(RepositoryApiPaths.FILE_DETAIL.replace("{id}", FILE_ID))
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FILE_NOT_FOUND"));
    }

    private static FileMetadataResponse metadata() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
        return new FileMetadataResponse(FILE_ID, "default", "docs", "reports", "local",
                "default/docs/2026/01/object", "report.txt", MediaType.TEXT_PLAIN_VALUE, "txt", 5,
                "checksum", "ACTIVE", "tester", now, now, Map.of("source", "test"));
    }

    private static RepositoryFileEntity entity() {
        RepositoryFileEntity entity = new RepositoryFileEntity();
        entity.setId(FILE_ID);
        entity.setTenantId("default");
        entity.setProvider(StorageProviderType.LOCAL);
        entity.setObjectKey("default/docs/2026/01/object");
        entity.setOriginalName("report.txt");
        entity.setContentType(MediaType.TEXT_PLAIN_VALUE);
        entity.setSize(5);
        entity.setStatus(FileStatus.ACTIVE);
        return entity;
    }
}