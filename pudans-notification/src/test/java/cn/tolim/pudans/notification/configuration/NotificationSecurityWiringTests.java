package cn.tolim.pudans.notification.configuration;

import cn.tolim.pudans.notification.controller.NotificationApiPaths;
import cn.tolim.pudans.notification.security.InternalTokenAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Verifies the notification security chain protects API paths and leaves probes public.
class NotificationSecurityWiringTests {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues("pudans.internal.token=" + INTERNAL_TOKEN);

    @Test
    void rejectsApiRequestWithoutInternalToken() {
        runner.run(context -> mockMvc(context)
                .perform(get(NotificationApiPaths.STATS))
                .andExpect(status().isUnauthorized()));
    }

    @Test
    void acceptsApiRequestWithInternalToken() {
        runner.run(context -> mockMvc(context)
                .perform(get(NotificationApiPaths.STATS)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("mounted")));
    }

    @Test
    void leavesHealthProbePublic() {
        runner.run(context -> mockMvc(context)
                .perform(get("/actuator/health"))
                .andExpect(status().isNotFound()));
    }

    private static MockMvc mockMvc(WebApplicationContext context) {
        FilterChainProxy securityFilterChain =
                context.getBean("springSecurityFilterChain", FilterChainProxy.class);
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(securityFilterChain)
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import(NotificationSecurityConfiguration.class)
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        StubNotificationController stubNotificationController() {
            return new StubNotificationController();
        }
    }

    @RestController
    static class StubNotificationController {

        @GetMapping(NotificationApiPaths.STATS)
        String stats() {
            return "mounted";
        }
    }
}