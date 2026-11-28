package cn.tolim.pudans.repository;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import cn.tolim.pudans.communal.web.GlobalExceptionHandler;
import cn.tolim.pudans.contract.repository.RepositoryApiPaths;
import cn.tolim.pudans.repository.contract.FileEndpoint;
import cn.tolim.pudans.repository.contract.FilePageResponse;
import cn.tolim.pudans.repository.config.RepositorySecurityConfiguration;
import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.security.InternalTokenAuthenticationFilter;
import cn.tolim.pudans.repository.service.FileStorageService;
import cn.tolim.pudans.repository.storage.ObjectStorageRegistry;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Boots the servlet context without PostgreSQL: DB/JPA/Liquibase auto-configuration is excluded
@SpringBootTest(classes = RepositoryApplicationTests.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "pudans.internal.token=repository-app-context-token",
                "pudans.repository.default-tenant-id=context-tenant",
                "pudans.repository.local.base-directory=build/test-repository-storage",
                "spissatus.self-check.on-startup=false"
        })
class RepositoryApplicationTests {

    private static final String INTERNAL_TOKEN = "repository-app-context-token";

    @LocalManagementPort
    private int managementPort;

    @LocalServerPort
    private int serverPort;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ObjectStorageRegistry objectStorageRegistry;

    @Autowired
    private RepositoryProperties repositoryProperties;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBean("springSecurityFilterChain",
                        Filter.class))
                .build();
    }

    @Test
    void bindsRepositoryPropertiesAndStorageRegistry() {
        assertThat(repositoryProperties.getDefaultTenantId()).isEqualTo("context-tenant");
        assertThat(objectStorageRegistry.defaultProvider()).isEqualTo(StorageProviderType.LOCAL);
        assertThat(objectStorageRegistry.isConfigured(StorageProviderType.LOCAL)).isTrue();
    }

    @Test
    void permitsActuatorHealthWithoutInternalToken() throws Exception {
        HttpResponse<String> response = httpGet(
                "http://127.0.0.1:" + managementPort + "/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
    }

    @Test
    void exposesPrometheusMetricsOnManagementPort() throws Exception {
        HttpResponse<String> response = httpGet(
                "http://127.0.0.1:" + managementPort + "/actuator/prometheus");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm_memory_used_bytes");
    }

    @Test
    void exposesMetricsIndexOnManagementPort() throws Exception {
        HttpResponse<String> response = httpGet(
                "http://127.0.0.1:" + managementPort + "/actuator/metrics");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("jvm.memory.used");
    }

    @Test
    void hidesActuatorFromApplicationPort() throws Exception {
        HttpResponse<String> response = httpGet(
                "http://127.0.0.1:" + serverPort + "/actuator/health");
        assertThat(response.statusCode()).isEqualTo(404);
    }

    private static HttpResponse<String> httpGet(String url) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void rejectsRepositoryApiWithoutInternalToken() throws Exception {
        mockMvc.perform(get(RepositoryApiPaths.PROVIDERS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.name()));
    }

    @Test
    void servesRepositoryApiWithInternalToken() throws Exception {
        when(fileStorageService.list(any()))
                .thenReturn(new FilePageResponse(List.of(), 0, 0, 20, 0));

        mockMvc.perform(get(RepositoryApiPaths.FILES)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.size").value(20));
    }

    @Test
    void exposesProvidersOnContractPath() throws Exception {
        mockMvc.perform(get(RepositoryApiPaths.PROVIDERS)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].provider").value("local"))
                .andExpect(jsonPath("$.data[0].defaultProvider").value(true));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceInitializationAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            DataJpaRepositoriesAutoConfiguration.class,
            LiquibaseAutoConfiguration.class
    })
    @Import({RepositorySecurityConfiguration.class, FileEndpoint.class, GlobalExceptionHandler.class})
    static class TestApplication {

        @Bean
        FileStorageService fileStorageService() {
            return Mockito.mock(FileStorageService.class);
        }

    }

}