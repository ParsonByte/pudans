package cn.tolim.pudans.search.security;

import cn.tolim.pudans.communal.core.error.CommonErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenAuthenticationFilterTests {

    private static final String INTERNAL_TOKEN = "test-internal-token";

    private final InternalTokenAuthenticationFilter filter =
            new InternalTokenAuthenticationFilter(INTERNAL_TOKEN, JsonMapper.builder().build());

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apis/pudans.tolim.cn/v1alpha1/search/query");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(CommonErrorCode.UNAUTHORIZED.name());
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsRequestWithWrongToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apis/pudans.tolim.cn/v1alpha1/search/query");
        request.addHeader(InternalTokenAuthenticationFilter.TOKEN_HEADER, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void acceptsRequestWithInternalToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/apis/pudans.tolim.cn/v1alpha1/search/query");
        request.addHeader(InternalTokenAuthenticationFilter.TOKEN_HEADER, INTERNAL_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication().isAuthenticated()).isTrue();
    }

    @Test
    void skipsTokenCheckForHealthProbe() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

}