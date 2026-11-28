package cn.tolim.pudans.notification.endpoint;

import cn.tolim.pudans.notification.configuration.NotificationSentinelBlockExceptionHandler;
import cn.tolim.pudans.notification.controller.NotificationApiPaths;
import cn.tolim.pudans.notification.controller.NotificationSendEndpoint;
import cn.tolim.pudans.notification.dto.SendNotificationResponse;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.enums.SendStatus;
import cn.tolim.pudans.notification.service.NotificationQueryService;
import cn.tolim.pudans.notification.service.NotificationSendService;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.SentinelWebInterceptor;
import com.alibaba.csp.sentinel.adapter.spring.webmvc_v6x.config.SentinelWebMvcConfig;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode;
import org.springframework.data.web.config.SpringDataJackson3Configuration;
import org.springframework.data.web.config.SpringDataWebSettings;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationSentinelTests {

    private final NotificationSendService sendService = mock(NotificationSendService.class);
    private final NotificationQueryService queryService = mock(NotificationQueryService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        JsonMapper jsonMapper = JsonMapper.builder()
                .addModule(new SpringDataJackson3Configuration.PageModule(
                        new SpringDataWebSettings(PageSerializationMode.DIRECT)))
                .build();
        NotificationSentinelBlockExceptionHandler handler =
                new NotificationSentinelBlockExceptionHandler(jsonMapper);
        SentinelWebMvcConfig config = new SentinelWebMvcConfig();
        config.setBlockExceptionHandler(handler);
        config.setWebContextUnify(false);
        SentinelWebInterceptor interceptor = new SentinelWebInterceptor(config);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new NotificationSendEndpoint(sendService, queryService))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(jsonMapper))
                .addInterceptors(interceptor)
                .build();
        FlowRule rule = new FlowRule(NotificationApiPaths.SEND);
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(1);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(List.of(rule));
    }

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(List.of());
    }

    @Test
    void blocksSendRequestsOverQpsLimit() throws Exception {
        SendNotificationResponse response = new SendNotificationResponse("record-1", "request-1",
                "welcome", NotificationChannel.EMAIL, "smtp", ProviderType.SMTP,
                "user@example.com", "Welcome", SendStatus.SUCCESS, 1, 3, null,
                "provider-message-1", null, null, null);
        when(sendService.send(any())).thenReturn(response);
        String body = """
                {"recipient":"user@example.com","channel":"EMAIL","content":"hello"}
                """;
        mockMvc.perform(post(NotificationApiPaths.SEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post(NotificationApiPaths.SEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("请求过于频繁"));
    }
}