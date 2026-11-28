package cn.tolim.pudans.mq.endpoint;

import cn.tolim.pudans.communal.web.GlobalExceptionHandler;
import cn.tolim.pudans.mq.compensation.MessageCompensationService;
import cn.tolim.pudans.mq.constant.MqApiPaths;
import cn.tolim.pudans.mq.enums.MessageStatus;
import cn.tolim.pudans.mq.model.MessagePublishRequest;
import cn.tolim.pudans.mq.model.MessagePublishResult;
import cn.tolim.pudans.mq.model.MessageStatsResponse;
import cn.tolim.pudans.mq.producer.MessageProducer;
import cn.tolim.pudans.mq.service.MessageCenterService;
import cn.tolim.pudans.mq.service.MessageDispatchCoordinator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(MessageCenterEndpointMvcTests.MvcSliceConfiguration.class)
@WebAppConfiguration
class MessageCenterEndpointMvcTests {

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    private MessageCenterService messageCenterService;

    @Autowired
    private MessageDispatchCoordinator messageDispatchCoordinator;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        reset(messageProducer, messageCenterService, messageDispatchCoordinator);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext).build();
    }

    @Test
    void mountsMessageCenterEndpointUnderApiMq() {
        assertThat(handlerMapping.getHandlerMethods().values())
                .anySatisfy(handler -> assertThat(handler.getBeanType())
                        .isEqualTo(MessageCenterEndpoint.class));

        Set<String> patterns = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());
        assertThat(patterns).contains(
                MqApiPaths.MESSAGES,
                MqApiPaths.MESSAGES + "/send",
                MqApiPaths.MESSAGES + "/{id}",
                MqApiPaths.DEAD_LETTERS,
                MqApiPaths.STATS,
                MqApiPaths.CONSUMERS,
                MqApiPaths.DISPATCH);
    }

    @Test
    void exposesMessageCenterStats() throws Exception {
        when(messageCenterService.stats()).thenReturn(new MessageStatsResponse(
                3, 1, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0));

        mockMvc.perform(get(MqApiPaths.STATS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.totalMessages").value(3))
                .andExpect(jsonPath("$.data.pendingMessages").value(1));

        verify(messageCenterService).stats();
    }

    @Test
    void publishesMessagesThroughProducer() throws Exception {
        when(messageProducer.publish(any(MessagePublishRequest.class)))
                .thenReturn(new MessagePublishResult("message-1", MessageStatus.PENDING, false,
                        null));

        mockMvc.perform(post(MqApiPaths.MESSAGES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"topic": "order.created", "payload": {"orderId": "42"}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.id").value("message-1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(messageProducer).publish(any(MessagePublishRequest.class));
    }

    @Test
    void rejectsPublishRequestWithoutTopic() throws Exception {
        mockMvc.perform(post(MqApiPaths.MESSAGES)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verifyNoInteractions(messageProducer);
    }

    @Test
    void triggersDispatchThroughCoordinator() throws Exception {
        when(messageDispatchCoordinator.dispatchDue()).thenReturn(2);

        mockMvc.perform(post(MqApiPaths.DISPATCH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        verify(messageDispatchCoordinator).dispatchDue();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(GlobalExceptionHandler.class)
    @ComponentScan(basePackageClasses = MessageCenterEndpoint.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE,
                    classes = MessageCenterEndpoint.class))
    static class MvcSliceConfiguration {

        @Bean
        MessageProducer messageProducer() {
            return mock(MessageProducer.class);
        }

        @Bean
        MessageCenterService messageCenterService() {
            return mock(MessageCenterService.class);
        }

        @Bean
        MessageCompensationService messageCompensationService() {
            return mock(MessageCompensationService.class);
        }

        @Bean
        MessageDispatchCoordinator messageDispatchCoordinator() {
            return mock(MessageDispatchCoordinator.class);
        }
    }
}