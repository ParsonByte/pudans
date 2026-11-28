package cn.tolim.pudans.iam.protocol.jwt.authentication;

import cn.tolim.pudans.iam.appkernel.jwt.model.JwtProtocolConfig;
import cn.tolim.pudans.iam.protocol.jwt.endpoint.JwtLogoutAuthenticationEndpointFilter;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtAuthenticationException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.POST_LOGOUT_REDIRECT_URI;
import static cn.tolim.pudans.iam.protocol.jwt.exception.JwtErrorCodes.SERVER_ERROR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class JwtLogoutAuthenticationProviderTests {

    private final JwtLogoutAuthenticationProvider provider = new JwtLogoutAuthenticationProvider();

    private static Authentication authenticatedPrincipal() {
        Authentication principal = mock(Authentication.class);
        when(principal.isAuthenticated()).thenReturn(true);
        return principal;
    }

    private static JwtProtocolConfig config(String postLogoutRedirectUri) {
        JwtProtocolConfig config = mock(JwtProtocolConfig.class);
        when(config.getPostLogoutRedirectUri()).thenReturn(postLogoutRedirectUri);
        return config;
    }

    @Test
    void returnsAuthenticatedTokenWhenRedirectUriMatches() {
        JwtProtocolConfig config = config("https://client.pudans.test/logout");
        JwtLogoutAuthenticationToken token = new JwtLogoutAuthenticationToken(
                authenticatedPrincipal(), config, "https://client.pudans.test/logout", null);

        Authentication result = provider.authenticate(token);

        assertThat(result).isSameAs(token);
        assertThat(result).isInstanceOf(JwtLogoutAuthenticationToken.class);
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    void filterCanCastProviderResultAndRedirects() throws Exception {
        JwtProtocolConfig config = config("https://client.pudans.test/logout");
        JwtLogoutAuthenticationToken token = new JwtLogoutAuthenticationToken(
                authenticatedPrincipal(), config, "https://client.pudans.test/logout", null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();
        RequestMatcher requestMatcher = mock(RequestMatcher.class);
        when(requestMatcher.matches(request)).thenReturn(true);
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenAnswer(invocation -> provider.authenticate(invocation.getArgument(0)));

        JwtLogoutAuthenticationEndpointFilter filter = new JwtLogoutAuthenticationEndpointFilter(
                requestMatcher, authenticationManager);
        filter.setAuthenticationConverter(ignored -> token);
        filter.setAuthenticationFailureHandler((AuthenticationFailureHandler) (
                ignored, failureResponse, exception) -> {
            throw new AssertionError("登出过滤器拒绝了已认证的令牌", exception);
        });

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getRedirectedUrl()).isEqualTo("https://client.pudans.test/logout");
    }

    @Test
    void rejectsMismatchedRedirectUri() {
        JwtProtocolConfig config = config("https://client.pudans.test/logout");
        JwtLogoutAuthenticationToken token = new JwtLogoutAuthenticationToken(
                authenticatedPrincipal(), config, "https://evil.pudans.test/logout", null);

        JwtAuthenticationException exception = assertThrows(JwtAuthenticationException.class,
                () -> provider.authenticate(token));

        assertThat(exception.getError().getErrorCode()).isEqualTo(SERVER_ERROR);
        assertThat(exception.getError().getDescription()).contains(POST_LOGOUT_REDIRECT_URI);
        assertThat(token.isAuthenticated()).isFalse();
    }

    @Test
    void rejectsMissingConfig() {
        JwtLogoutAuthenticationToken token = new JwtLogoutAuthenticationToken(
                authenticatedPrincipal(), null, "https://client.pudans.test/logout", null);

        assertThrows(JwtAuthenticationException.class, () -> provider.authenticate(token));
        assertThat(token.isAuthenticated()).isFalse();
    }

    @Test
    void rejectsBlankConfiguredRedirectUri() {
        JwtLogoutAuthenticationToken token = new JwtLogoutAuthenticationToken(
                authenticatedPrincipal(), config(" "), "https://client.pudans.test/logout", null);

        assertThrows(JwtAuthenticationException.class, () -> provider.authenticate(token));
        assertThat(token.isAuthenticated()).isFalse();
    }

    @Test
    void rejectsNullAuthentication() {
        assertThrows(JwtAuthenticationException.class, () -> provider.authenticate(null));
    }
}
