package cn.tolim.pudans.notification.endpoint;

import cn.tolim.pudans.notification.controller.NotificationApiPaths;
import cn.tolim.pudans.notification.controller.NotificationProviderEndpoint;
import cn.tolim.pudans.notification.controller.NotificationSendEndpoint;
import cn.tolim.pudans.notification.controller.NotificationTemplateEndpoint;
import cn.tolim.pudans.notification.dto.NotificationStatsResponse;
import cn.tolim.pudans.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.enums.SendStatus;
import cn.tolim.pudans.notification.service.NotificationProviderService;
import cn.tolim.pudans.notification.service.NotificationQueryService;
import cn.tolim.pudans.notification.service.NotificationSendService;
import cn.tolim.pudans.notification.service.NotificationTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.data.web.config.SpringDataJackson3Configuration;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies every notification controller is mounted on the paths advertised by the module.
class NotificationEndpointMappingTests {

    private final NotificationSendService sendService = mock(NotificationSendService.class);
    private final NotificationQueryService queryService = mock(NotificationQueryService.class);
    private final NotificationProviderService providerService =
            mock(NotificationProviderService.class);
    private final NotificationTemplateService templateService =
            mock(NotificationTemplateService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Mirrors the application Jackson setup so Page responses serialize like production.
        JsonMapper jsonMapper = JsonMapper.builder()
                .addModule(new SpringDataJackson3Configuration.PageModule(
                        new SpringDataWebSettings(PageSerializationMode.DIRECT)))
                .build();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new NotificationSendEndpoint(sendService, queryService),
                        new NotificationProviderEndpoint(providerService),
                        new NotificationTemplateEndpoint(templateService, queryService))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .build();
    }

    @Test
    void sendsNotification() throws Exception {
        SendNotificationResponse response = new SendNotificationResponse("record-1",
                "request-1", "welcome", NotificationChannel.EMAIL, "smtp",
                ProviderType.SMTP, "user@example.com", "Welcome", SendStatus.SUCCESS, 1, 3,
                null, "provider-message-1", null, null, null);
        when(sendService.send(any())).thenReturn(response);

        mockMvc.perform(post(NotificationApiPaths.SEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipient":"user@example.com","channel":"EMAIL","content":"hello"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.id").value("record-1"))
                .andExpect(jsonPath("$.result.status").value("SUCCESS"));
    }

    @Test
    void listsSendRecords() throws Exception {
        when(queryService.searchSendRecords(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(emptyPage());

        mockMvc.perform(get(NotificationApiPaths.RECORDS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listsDeadLetters() throws Exception {
        when(queryService.searchDeadLetters(any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get(NotificationApiPaths.DEAD_LETTERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void returnsSendStatistics() throws Exception {
        when(queryService.stats())
                .thenReturn(new NotificationStatsResponse(Map.of(SendStatus.SUCCESS, 1L), 0L));

        mockMvc.perform(get(NotificationApiPaths.STATS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.pendingDeadLetters").value(0));
    }

    @Test
    void listsTemplates() throws Exception {
        when(queryService.searchTemplates(any(), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get(NotificationApiPaths.TEMPLATES))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void listsProviders() throws Exception {
        when(providerService.list(any(), any(), any(), any())).thenReturn(emptyPage());

        mockMvc.perform(get(NotificationApiPaths.PROVIDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    private static <T> Page<T> emptyPage() {
        return new PageImpl<>(List.of());
    }
}