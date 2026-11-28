package cn.tolim.pudans.mq.config;

import cn.tolim.pudans.mq.constant.MqApiPaths;
import cn.tolim.pudans.mq.security.InternalTokenAuthenticationFilter;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(MqSecurityConfigurationTests.SecuritySliceConfiguration.class)
@WebAppConfiguration
@TestPropertySource(properties = "pudans.internal.token=" + MqSecurityConfigurationTests.INTERNAL_TOKEN)
class MqSecurityConfigurationTests {

    static final String INTERNAL_TOKEN = "security-slice-token";

    @Autowired
    private WebApplicationContext applicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter springSecurityFilterChain =
                (Filter) applicationContext.getBean("springSecurityFilterChain");
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void rejectsRequestsWithoutInternalToken() throws Exception {
        mockMvc.perform(get(MqApiPaths.STATS))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsRequestsWithWrongInternalToken() throws Exception {
        mockMvc.perform(get(MqApiPaths.STATS)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, "wrong-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsRequestsWithInternalToken() throws Exception {
        mockMvc.perform(get(MqApiPaths.STATS)
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void exposesHealthProbeWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("UP"));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @EnableWebMvc
    @Import(MqSecurityConfiguration.class)
    static class SecuritySliceConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        ProbeEndpoint probeEndpoint() {
            return new ProbeEndpoint();
        }
    }

    @RestController
    static class ProbeEndpoint {

        @GetMapping(MqApiPaths.STATS)
        String stats() {
            return "ok";
        }

        @GetMapping("/actuator/health")
        String health() {
            return "UP";
        }
    }
}