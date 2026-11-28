package cn.tolim.pudans.job;

import cn.tolim.pudans.communal.core.contract.ApiResponse;
import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import cn.tolim.pudans.job.contract.JobApiExceptionHandler;
import cn.tolim.pudans.job.contract.JobDefinitionEndpoint;
import cn.tolim.pudans.job.contract.JobExecutionEndpoint;
import cn.tolim.pudans.job.contract.JobExecutorEndpoint;
import cn.tolim.pudans.job.configuration.JobExecutorProperties;
import cn.tolim.pudans.job.configuration.JobSecurityConfiguration;
import cn.tolim.pudans.job.dto.JobHandlerDescriptor;
import cn.tolim.pudans.job.enums.ExecutionStatus;
import cn.tolim.pudans.job.enums.JobStatus;
import cn.tolim.pudans.job.handler.JobHandlerRegistry;
import cn.tolim.pudans.job.repository.JobDefinitionRepository;
import cn.tolim.pudans.job.repository.JobExecutionRepository;
import cn.tolim.pudans.job.scheduling.JobScheduleManager;
import cn.tolim.pudans.job.security.InternalTokenAuthenticationFilter;
import cn.tolim.pudans.job.service.JobDefinitionService;
import cn.tolim.pudans.job.service.JobExecutionQueryService;
import cn.tolim.pudans.job.service.JobExecutionService;
import cn.tolim.pudans.job.support.JobConstants;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies the Spring Boot host app mounts the job controllers behind internal-token security
@SpringJUnitWebConfig(classes = JobWebLayerTests.WebTestConfiguration.class)
@TestPropertySource(properties = "pudans.internal.token=" + JobWebLayerTests.INTERNAL_TOKEN)
class JobWebLayerTests {

    static final String INTERNAL_TOKEN = "job-test-internal-token";

    private static final String NOOP_HANDLER = JobConstants.NOOP_HANDLER;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    @MockitoBean
    private JobDefinitionService jobDefinitionService;

    @MockitoBean
    private JobExecutionService jobExecutionService;

    @MockitoBean
    private JobExecutionQueryService jobExecutionQueryService;

    @MockitoBean
    private JobHandlerRegistry jobHandlerRegistry;

    @MockitoBean
    private JobScheduleManager jobScheduleManager;

    @MockitoBean
    private JobDefinitionRepository jobDefinitionRepository;

    @MockitoBean
    private JobExecutionRepository jobExecutionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(webApplicationContext.getBeansOfType(Filter.class)
                        .values().toArray(Filter[]::new))
                .build();
        when(jobHandlerRegistry.names()).thenReturn(List.of(NOOP_HANDLER));
        when(jobHandlerRegistry.descriptors()).thenReturn(List.of(
                new JobHandlerDescriptor(NOOP_HANDLER, "No-op job handler",
                        "cn.tolim.pudans.job.handler.NoopJobHandler")));
    }

    @Test
    void mountsJobControllersUnderTheVersionedApiPaths() {
        Map<RequestMappingInfo, HandlerMethod> handlers =
                requestMappingHandlerMapping.getHandlerMethods();

        assertThat(handlers.values())
                .extracting(HandlerMethod::getBeanType)
                .contains(JobDefinitionEndpoint.class, JobExecutionEndpoint.class,
                        JobExecutorEndpoint.class);

        Set<String> patterns = handlers.keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns).contains(
                JobConstants.JOBS_PATH,
                JobConstants.JOBS_PATH + "/{id}",
                JobConstants.JOBS_PATH + "/{id}/trigger",
                JobConstants.JOBS_PATH + "/handlers",
                JobConstants.EXECUTIONS_PATH,
                JobConstants.EXECUTIONS_PATH + "/{id}/cancel",
                JobConstants.EXECUTIONS_PATH + "/stats/{jobId}",
                JobConstants.EXECUTOR_PATH + "/status",
                JobConstants.EXECUTOR_PATH + "/scheduled");
    }

    @Test
    void rejectsApiCallWithoutInternalToken() throws Exception {
        mockMvc.perform(get(JobConstants.JOBS_PATH + "/handlers"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void rejectsApiCallWithWrongInternalToken() throws Exception {
        mockMvc.perform(get(JobConstants.JOBS_PATH + "/handlers")
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(CommonErrorCode.UNAUTHORIZED.code()));
    }

    @Test
    void allowsHealthProbeWithoutInternalToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void servesJobHandlersForAuthenticatedInternalCall() throws Exception {
        mockMvc.perform(get(JobConstants.JOBS_PATH + "/handlers")
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data[0].name").value(NOOP_HANDLER));
    }

    @Test
    void servesExecutorStatusForAuthenticatedInternalCall() throws Exception {
        when(jobDefinitionRepository.count()).thenReturn(4L);
        when(jobDefinitionRepository.countByStatus(JobStatus.ENABLED)).thenReturn(3L);
        when(jobExecutionRepository.countByStatus(ExecutionStatus.QUEUED)).thenReturn(1L);
        when(jobExecutionRepository.countByStatus(ExecutionStatus.RUNNING)).thenReturn(2L);
        when(jobScheduleManager.scheduledCount()).thenReturn(3);

        mockMvc.perform(get(JobConstants.EXECUTOR_PATH + "/status")
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ApiResponse.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.instanceName").value("job-test"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.scheduledJobCount").value(3))
                .andExpect(jsonPath("$.data.jobDefinitionCount").value(4))
                .andExpect(jsonPath("$.data.enabledJobCount").value(3))
                .andExpect(jsonPath("$.data.queuedExecutionCount").value(1))
                .andExpect(jsonPath("$.data.runningExecutionCount").value(2))
                .andExpect(jsonPath("$.data.handlerNames[0]").value(NOOP_HANDLER));
    }

    @Test
    void returnsJobValidationErrorEnvelopeFromMountedAdvice() throws Exception {
        mockMvc.perform(post(JobConstants.JOBS_PATH)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "", "name": "", "handlerName": "", "scheduleType": null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_INVALID_REQUEST"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    // Minimal MVC + security slice: real controllers/advice, mocked persistence and services
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({JobSecurityConfiguration.class, JobDefinitionEndpoint.class,
            JobExecutionEndpoint.class, JobExecutorEndpoint.class,
            JobApiExceptionHandler.class, HealthProbeStub.class})
    static class WebTestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        JobExecutorProperties jobExecutorProperties() {
            JobExecutorProperties properties = new JobExecutorProperties();
            properties.setInstanceName("job-test");
            properties.setEnabled(true);
            properties.setAutoStart(false);
            return properties;
        }

        @Bean(name = "jobExecutionExecutor")
        ThreadPoolTaskExecutor jobExecutionExecutor() {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setCorePoolSize(1);
            executor.setMaxPoolSize(1);
            executor.setQueueCapacity(10);
            executor.setThreadNamePrefix("job-test-worker-");
            executor.initialize();
            return executor;
        }
    }

    // Stands in for the actuator endpoint that the real app exposes on the same path
    @RestController
    static class HealthProbeStub {

        @GetMapping("/actuator/health")
        Map<String, String> health() {
            return Map.of("status", "UP");
        }
    }
}