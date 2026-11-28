package cn.tolim.pudans.search.web;

import cn.tolim.pudans.search.config.SearchSecurityConfiguration;
import cn.tolim.pudans.search.constant.SearchApiPaths;
import cn.tolim.pudans.search.controller.SearchDocumentEndpoint;
import cn.tolim.pudans.search.controller.SearchIndexEndpoint;
import cn.tolim.pudans.search.controller.SearchQueryEndpoint;
import cn.tolim.pudans.search.error.SearchErrorCode;
import cn.tolim.pudans.search.error.SearchException;
import cn.tolim.pudans.search.model.SearchIndexResponse;
import cn.tolim.pudans.search.model.SearchPageResponse;
import cn.tolim.pudans.search.model.SearchQuery;
import cn.tolim.pudans.search.security.InternalTokenAuthenticationFilter;
import cn.tolim.pudans.search.service.SearchDocumentService;
import cn.tolim.pudans.search.service.SearchIndexService;
import cn.tolim.pudans.search.service.SearchQueryService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Boots the web layer only, so controller mounting and security are verified without a database
@SpringJUnitConfig(classes = {SearchWebContextTests.TestConfiguration.class,
        SearchSecurityConfiguration.class})
@WebAppConfiguration
@TestPropertySource(properties = "pudans.internal.token=" + SearchWebContextTests.INTERNAL_TOKEN)
class SearchWebContextTests {

    static final String INTERNAL_TOKEN = "test-internal-token";

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private SearchIndexService indexService;

    @Autowired
    private SearchDocumentService documentService;

    @Autowired
    private SearchQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Filter securityFilterChain = applicationContext.getBean("springSecurityFilterChain", Filter.class);
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(securityFilterChain)
                .build();
        reset(indexService, documentService, queryService);
    }

    @Test
    void mountsEverySearchController() {
        assertThat(applicationContext.getBeansOfType(SearchIndexEndpoint.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(SearchDocumentEndpoint.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(SearchQueryEndpoint.class)).hasSize(1);

        RequestMappingHandlerMapping mapping = applicationContext.getBean(
                "requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Set<String> patterns = mapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());

        assertThat(patterns).contains(
                SearchApiPaths.INDEXES,
                SearchApiPaths.INDEXES + "/{indexCode}",
                SearchApiPaths.INDEXES + "/{indexCode}/documents",
                SearchApiPaths.INDEXES + "/{indexCode}/documents/batch",
                SearchApiPaths.INDEXES + "/{indexCode}/documents/{documentId}",
                SearchApiPaths.QUERY);
    }

    @Test
    void rejectsRequestsWithoutInternalToken() throws Exception {
        mockMvc.perform(get(SearchApiPaths.QUERY).param("appCode", "orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        verifyNoInteractions(queryService);
    }

    @Test
    void delegatesAuthenticatedQueryToSearchService() throws Exception {
        when(queryService.search(any(SearchQuery.class)))
                .thenReturn(new SearchPageResponse<>(List.of(), 1, 20, 0, 0, false));

        mockMvc.perform(get(SearchApiPaths.QUERY)
                        .param("appCode", "orders")
                        .param("indexCode", "articles")
                        .param("q", "refund")
                        .param("page", "2")
                        .param("size", "5")
                        .param("sort", "UPDATED_DESC")
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items").isEmpty());

        ArgumentCaptor<SearchQuery> captor = ArgumentCaptor.forClass(SearchQuery.class);
        verify(queryService).search(captor.capture());
        SearchQuery query = captor.getValue();
        assertThat(query.appCode()).isEqualTo("orders");
        assertThat(query.indexCode()).isEqualTo("articles");
        assertThat(query.keyword()).isEqualTo("refund");
        assertThat(query.page()).isEqualTo(2);
        assertThat(query.size()).isEqualTo(5);
        assertThat(query.sort()).isEqualTo("UPDATED_DESC");
    }

    @Test
    void exposesIndexManagementEndpoints() throws Exception {
        when(indexService.listIndexes("orders")).thenReturn(List.of(new SearchIndexResponse("index-1",
                "orders", "articles", "Articles", null, true, 3,
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 1, 2, 0, 0))));

        mockMvc.perform(get(SearchApiPaths.INDEXES)
                        .param("appCode", "orders")
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].indexCode").value("articles"))
                .andExpect(jsonPath("$.data[0].documentCount").value(3));
    }

    @Test
    void mapsSearchErrorsToApiEnvelope() throws Exception {
        when(queryService.search(any(SearchQuery.class)))
                .thenThrow(new SearchException(SearchErrorCode.INVALID_QUERY, "appCode is required"));

        mockMvc.perform(get(SearchApiPaths.QUERY)
                        .param("appCode", " ")
                        .header(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(SearchErrorCode.INVALID_QUERY.getCode()))
                .andExpect(jsonPath("$.message").value("appCode is required"));
    }

    @Test
    void permitsHealthProbeWithoutInternalToken() throws Exception {
        // The probe is public in the filter chain, so it reaches MVC and reports a missing handler
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @ComponentScan(basePackages = {
            "cn.tolim.pudans.search.controller", "cn.tolim.pudans.search.error"})
    static class TestConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().build();
        }

        @Bean
        SearchIndexService searchIndexService() {
            return mock(SearchIndexService.class);
        }

        @Bean
        SearchDocumentService searchDocumentService() {
            return mock(SearchDocumentService.class);
        }

        @Bean
        SearchQueryService searchQueryService() {
            return mock(SearchQueryService.class);
        }
    }
}