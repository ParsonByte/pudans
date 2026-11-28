package cn.tolim.pudans.iam.protocol.oidc.endpoint;

import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContext;
import cn.tolim.pudans.iam.appkernel.core.context.ApplicationContextHolder;
import cn.tolim.pudans.iam.appkernel.oidc.model.OidcProtocolConfig;
import cn.tolim.pudans.iam.infra.security.util.AntPathRequestMatcher;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationImplicitAccessTokenAuthenticationToken;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationImplicitRequestAuthenticationException;
import cn.tolim.pudans.iam.protocol.oidc.authentication.OAuth2AuthorizationImplicitRequestAuthenticationToken;
import cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication.OAuth2AuthorizationImplicitRequestAuthenticationConverter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.log.LogMessage;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.web.authentication.OAuth2AuthorizationCodeRequestAuthenticationConverter;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.OidcEndpointConstants.AUTHORIZATION_ENDPOINT;
import static cn.tolim.pudans.iam.protocol.oidc.constant.OidcProtocolConstants.OIDC_ERROR_URI;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2EndpointUtils.appendUrl;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2ParameterNames.FRAGMENT;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.OAuth2ParameterNames.RESPONSE_MODE;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication.OAuth2AuthorizationImplicitRequestAuthenticationConverter.ID_TOKEN;
import static cn.tolim.pudans.iam.protocol.oidc.endpoint.authentication.OAuth2AuthorizationImplicitRequestAuthenticationConverter.TOKEN;
import static org.springframework.security.oauth2.core.OAuth2ErrorCodes.UNSUPPORTED_RESPONSE_TYPE;
import static org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames.TOKEN_TYPE;
import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.*;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public final class OAuth2AuthorizationEndpointFilter extends OncePerRequestFilter {

    private static final Authentication ANONYMOUS_AUTHENTICATION = new AnonymousAuthenticationToken(
            "anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
    // OAuth2AuthorizationService
    private final OAuth2AuthorizationService authorizationService;
    // Authentication manager
    private final AuthenticationManager authenticationManager;
    // Authorization endpoint matcher
    private final RequestMatcher authorizationEndpointMatcher;
    // Redirect policy
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();
    // AuthenticationDetailsSource
    private AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource = new WebAuthenticationDetailsSource();
    // Authentication converter
    private AuthenticationConverter authenticationConverter;
    // Authentication success handler
    private AuthenticationSuccessHandler authenticationSuccessHandler = this::sendAuthorizationResponse;
    // Authentication failure handler
    private AuthenticationFailureHandler authenticationFailureHandler = this::sendErrorResponse;
    // Session identity policy
    private SessionAuthenticationStrategy sessionAuthenticationStrategy = (authentication,
                                                                           request,
                                                                           response) -> {
    };

    // Constructs an OAuth2AuthorizationEndpointFilter using the provided parameters.
    // Parameter authenticationManager the authentication manager
    // Parameter authorizationService the authorization service
    public OAuth2AuthorizationEndpointFilter(AuthenticationManager authenticationManager,
                                             OAuth2AuthorizationService authorizationService) {
        this(authorizationService, authenticationManager, AUTHORIZATION_ENDPOINT);
    }

    // Constructs an OAuth2AuthorizationEndpointFilter using the provided parameters.
    // Parameter authorizationService the authorization service
    // Parameter authenticationManager the authentication manager
    // Parameter authorizationEndpointUri the endpoint URI for authorization requests
    public OAuth2AuthorizationEndpointFilter(OAuth2AuthorizationService authorizationService,
                                             AuthenticationManager authenticationManager,
                                             String authorizationEndpointUri) {
        this.authorizationService = authorizationService;
        Assert.notNull(authenticationManager, "authenticationManager 不能为空");
        Assert.hasText(authorizationEndpointUri, "authorizationEndpointUri 不能为空");
        this.authenticationManager = authenticationManager;
        this.authorizationEndpointMatcher = createDefaultRequestMatcher(authorizationEndpointUri);
        this.authenticationConverter = request -> {
            // Step 1: allow only GET and POST requests and check for request parameters; without a request object, use self-initiated authentication mode
            if (isRequestWithoutParams(request)) {
                // Get application configuration
                ApplicationContext context = ApplicationContextHolder.getApplicationContext();
                String clientId = context.getClientId();
                OidcProtocolConfig config = (OidcProtocolConfig) context.getConfig().get(OidcProtocolConfig.class.getName());

                String authorizationUri = request.getRequestURL().toString();

                Authentication principal = SecurityContextHolder.getContext().getAuthentication();
                if (principal == null) {
                    principal = ANONYMOUS_AUTHENTICATION;
                }
                // Redirect URI not configured
                if (CollectionUtils.isEmpty(config.getRedirectUris())){
                    OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.INVALID_REQUEST, "OAuth 2.0 参数：" + OAuth2ParameterNames.REDIRECT_URI,null);
                    throw new OAuth2AuthorizationCodeRequestAuthenticationException(error, null);
                }
                return new OAuth2AuthorizationCodeRequestAuthenticationToken(authorizationUri, clientId, principal, new ArrayList<>(config.getRedirectUris()).get(0), RandomStringUtils.randomAlphanumeric(32).toLowerCase(), config.getGrantScopes(),null);
            }
            try {
                // Step 2: if step 1 returns null, try the authorization code mode converter (built into Spring Authorization Server)
                return new OAuth2AuthorizationCodeRequestAuthenticationConverter().convert(request);
            } catch (OAuth2AuthorizationCodeRequestAuthenticationException e) {
                // Step 3: throw unsupported_response_type and try the implicit mode converter
                if (UNSUPPORTED_RESPONSE_TYPE.equals(e.getError().getErrorCode())) {
                    return new OAuth2AuthorizationImplicitRequestAuthenticationConverter().convert(request);
                }
                throw e;
            }
        };
    }

    // Create default request matcher
    // Parameter authorizationEndpointUri String Authorization endpoint URI
    // Returns RequestMatcher
    private static RequestMatcher createDefaultRequestMatcher(String authorizationEndpointUri) {
        // GET request
        RequestMatcher authorizationRequestGetMatcher = new AntPathRequestMatcher(
                authorizationEndpointUri, HttpMethod.GET.name());
        // POST request
        RequestMatcher authorizationRequestPostMatcher = new AntPathRequestMatcher(
                authorizationEndpointUri, HttpMethod.POST.name());
        // OpenID scope matching
        RequestMatcher openidScopeMatcher = request -> {
            String scope = request.getParameter(OAuth2ParameterNames.SCOPE);
            return StringUtils.isNotBlank(scope) && scope.contains(OidcScopes.OPENID);
        };
        // response_type parameter matching
        RequestMatcher responseTypeParameterMatcher = request -> request
                .getParameter(OAuth2ParameterNames.RESPONSE_TYPE) != null;

        RequestMatcher authorizationRequestMatcher = new OrRequestMatcher(
                authorizationRequestGetMatcher, new AndRequestMatcher(authorizationRequestPostMatcher,
                responseTypeParameterMatcher, openidScopeMatcher));
        RequestMatcher authorizationConsentMatcher = new AndRequestMatcher(
                authorizationRequestPostMatcher,
                new NegatedRequestMatcher(responseTypeParameterMatcher));

        return new OrRequestMatcher(authorizationRequestMatcher, authorizationConsentMatcher);
    }

    // Filter
    // Parameter request HttpServletRequest
    // Parameter response HttpServletResponse
    // Parameter filterChain FilterChain
    // Throws ServletException ServletException
    // Throws IOException IOException
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException,
            IOException {

        if (!this.authorizationEndpointMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Convert through the authentication converter
            Authentication authentication = this.authenticationConverter.convert(request);
            if (authentication instanceof AbstractAuthenticationToken) {
                ((AbstractAuthenticationToken) authentication)
                        .setDetails(this.authenticationDetailsSource.buildDetails(request));
            }

            // Invoke the authentication manager
            Authentication authenticationResult = this.authenticationManager
                    .authenticate(authentication);

            if (!authenticationResult.isAuthenticated()) {
                // If the Principal (Resource Owner) is not authenticated then
                // pass through the chain with the expectation that the authentication process
                // will commence via AuthenticationEntryPoint
                filterChain.doFilter(request, response);
                return;
            }
            this.sessionAuthenticationStrategy.onAuthentication(authenticationResult, request,
                    response);
            this.authenticationSuccessHandler.onAuthenticationSuccess(request, response,
                    authenticationResult);
        } catch (OAuth2AuthenticationException ex) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace(
                        LogMessage.format("授权请求失败：%s", ex.getError()), ex);
            }
            this.authenticationFailureHandler.onAuthenticationFailure(request, response, ex);
        }
    }

    // Send success response
    // Parameter request HttpServletRequest
    // Parameter response HttpServletResponse
    // Parameter authentication Authentication
    // Throws IOException IOException
    private void sendAuthorizationResponse(HttpServletRequest request, HttpServletResponse response,
                                           Authentication authentication) throws IOException {
        // Authorization code request
        if (authentication instanceof OAuth2AuthorizationCodeRequestAuthenticationToken authenticationToken) {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder
                    .fromUriString(Objects.toString(authenticationToken.getRedirectUri()))
                    .queryParam(OAuth2ParameterNames.CODE,
                            ObjectUtils.isNotEmpty(authenticationToken.getAuthorizationCode())
                                    ? authenticationToken.getAuthorizationCode().getTokenValue()
                                    : "");
            if (StringUtils.isNotBlank(authenticationToken.getState())) {
                uriBuilder.queryParam(OAuth2ParameterNames.STATE,
                        UriUtils.encode(authenticationToken.getState(), StandardCharsets.UTF_8));
            }
            // build(true) -> Components are explicitly encoded
            String redirectUri = uriBuilder.build(true).toUriString();
            this.redirectStrategy.sendRedirect(request, response, redirectUri);
            return;
        }
        // Implicit mode
        if (authentication instanceof OAuth2AuthorizationImplicitAccessTokenAuthenticationToken authenticationToken) {
            Map<String, Object> vars = new LinkedHashMap<>();
            Map<String, String> keys = new HashMap<>();
            // Response type
            String responseType = (String) authenticationToken.getAdditionalParameters()
                    .get(RESPONSE_TYPE);
            Set<String> responseTypes = new HashSet<>(
                    Arrays.asList(org.springframework.util.StringUtils
                            .delimitedListToStringArray(responseType, " ")));
            // Response type contains token
            if (responseTypes.contains(TOKEN.getValue())) {
                OAuth2AccessToken accessToken = authenticationToken.getAccessToken();
                vars.put(TOKEN_TYPE, accessToken.getTokenType().getValue());
                vars.put(ACCESS_TOKEN, accessToken.getTokenValue());
                if (accessToken.getIssuedAt() != null && accessToken.getExpiresAt() != null) {
                    vars.put(EXPIRES_IN, ChronoUnit.SECONDS.between(accessToken.getIssuedAt(),
                            accessToken.getExpiresAt()));
                }
            }
            // id_token
            if (responseTypes.contains(ID_TOKEN.getValue())) {
                vars.put(OidcParameterNames.ID_TOKEN,
                        authenticationToken.getAdditionalParameters().get(OidcParameterNames.ID_TOKEN));
            }
            // state
            String state = (String) authenticationToken.getAdditionalParameters().get(STATE);
            // redirectUri
            String redirectUri = (String) authenticationToken.getAdditionalParameters()
                    .get(REDIRECT_URI);
            if (org.springframework.util.StringUtils.hasText(state)) {
                vars.put(OAuth2ParameterNames.STATE, state);
            }
            String responseMode = (String) authenticationToken.getAdditionalParameters()
                    .get(RESPONSE_MODE);
            // If the parameter is empty or fragment, append the parameters to the URL
            if (StringUtils.isBlank(responseMode) || StringUtils.equals(responseMode, FRAGMENT)) {
                redirectUri = appendUrl(redirectUri, vars, keys, true);
            }
            // Query mode
            else {
                redirectUri = appendUrl(redirectUri, vars, keys, false);
            }
            this.redirectStrategy.sendRedirect(request, response, redirectUri);
        }
    }

    // Send error response
    // Parameter request HttpServletRequest
    // Parameter response HttpServletResponse
    // Parameter exception AuthenticationException
    // Throws IOException IOException
    private void sendErrorResponse(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException exception) throws IOException {
        OAuth2Error error = null;
        AbstractAuthenticationToken requestAuthenticationToken = null;
        String redirectUri = null;
        String state = null;
        // Implicit grant mode
        if (exception instanceof OAuth2AuthorizationImplicitRequestAuthenticationException e) {
            error = e.getError();
            // Get request object
            OAuth2AuthorizationImplicitRequestAuthenticationToken authenticationToken = e
                    .getAuthorizationImplicitRequestAuthenticationToken();
            if (!Objects.isNull(authenticationToken)) {
                requestAuthenticationToken = authenticationToken;
                // Get redirect address
                redirectUri = authenticationToken.getRedirectUri();
                // Get state
                state = authenticationToken.getState();
            }
        }
        // Authorization code mode
        if (exception instanceof OAuth2AuthorizationCodeRequestAuthenticationException e) {
            error = e.getError();

            // Get request object
            OAuth2AuthorizationCodeRequestAuthenticationToken authenticationToken = e
                    .getAuthorizationCodeRequestAuthentication();
            if (!Objects.isNull(authenticationToken)) {
                requestAuthenticationToken = authenticationToken;
                // Get redirect address
                redirectUri = authenticationToken.getRedirectUri();
                // Get state
                state = authenticationToken.getState();
            }

        }

        if (Objects.isNull(error)) {
            return;
        }

        // Wrap error response
        OAuth2Error responseError = new OAuth2Error(error.getErrorCode(), error.getDescription(),
                OIDC_ERROR_URI);

        if (Objects.isNull(requestAuthenticationToken) || !StringUtils.isNotBlank(redirectUri)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), responseError.toString());
            return;
        }

        if (this.logger.isTraceEnabled()) {
            this.logger.trace("携带错误重定向到客户端");
        }

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(redirectUri)
                .queryParam(OAuth2ParameterNames.ERROR, responseError.getErrorCode());
        if (StringUtils.isNotBlank(responseError.getDescription())) {
            uriBuilder.queryParam(OAuth2ParameterNames.ERROR_DESCRIPTION,
                    UriUtils.encode(responseError.getDescription(), StandardCharsets.UTF_8));
        }
        if (StringUtils.isNotBlank(responseError.getUri())) {
            uriBuilder.queryParam(OAuth2ParameterNames.ERROR_URI,
                    UriUtils.encode(responseError.getUri(), StandardCharsets.UTF_8));
        }
        if (StringUtils.isNotBlank(state)) {
            uriBuilder.queryParam(OAuth2ParameterNames.STATE,
                    UriUtils.encode(state, StandardCharsets.UTF_8));
        }
        // build(true) -> Components are explicitly encoded
        redirectUri = uriBuilder.build(true).toUriString();
        this.redirectStrategy.sendRedirect(request, response, redirectUri);

    }

    private boolean isRequestWithoutParams(HttpServletRequest request) {
        Map<String, String[]> paramMap = request.getParameterMap();
        return paramMap.isEmpty();
    }

    // Sets the AuthenticationDetailsSource used for building an authentication details instance from HttpServletRequest.
    // Parameter authenticationDetailsSource the AuthenticationDetailsSource used for building an authentication details instance from HttpServletRequest
    public void setAuthenticationDetailsSource(AuthenticationDetailsSource<HttpServletRequest, ?> authenticationDetailsSource) {
        Assert.notNull(authenticationDetailsSource, "authenticationDetailsSource 不能为空");
        this.authenticationDetailsSource = authenticationDetailsSource;
    }

    // Sets the AuthenticationConverter used when attempting to extract an Authorization Request (or Consent) from HttpServletRequest
    // to an instance of OAuth2AuthorizationCodeRequestAuthenticationToken or OAuth2AuthorizationConsentAuthenticationToken
    // used for authenticating the request.
    // Parameter authenticationConverter the AuthenticationConverter used when attempting to extract an Authorization Request (or Consent) from HttpServletRequest
    public void setAuthenticationConverter(AuthenticationConverter authenticationConverter) {
        Assert.notNull(authenticationConverter, "authenticationConverter 不能为空");
        this.authenticationConverter = authenticationConverter;
    }

    // Sets the AuthenticationSuccessHandler used for handling an OAuth2AuthorizationCodeRequestAuthenticationToken
    // and returning the Authorization Response.
    // Parameter authenticationSuccessHandler the AuthenticationSuccessHandler used for handling an OAuth2AuthorizationCodeRequestAuthenticationToken
    public void setAuthenticationSuccessHandler(AuthenticationSuccessHandler authenticationSuccessHandler) {
        Assert.notNull(authenticationSuccessHandler, "authenticationSuccessHandler 不能为空");
        this.authenticationSuccessHandler = authenticationSuccessHandler;
    }

    // Sets the AuthenticationFailureHandler used for handling an OAuth2AuthorizationCodeRequestAuthenticationException
    // and returning the Error Response.
    // Parameter authenticationFailureHandler the AuthenticationFailureHandler used for handling an OAuth2AuthorizationCodeRequestAuthenticationException
    public void setAuthenticationFailureHandler(AuthenticationFailureHandler authenticationFailureHandler) {
        Assert.notNull(authenticationFailureHandler, "authenticationFailureHandler 不能为空");
        this.authenticationFailureHandler = authenticationFailureHandler;
    }

    // Sets the SessionAuthenticationStrategy used for handling an OAuth2AuthorizationCodeRequestAuthenticationToken
    // before calling the AuthenticationSuccessHandler.
    // If OpenID Connect is enabled, the default implementation tracks OpenID Connect sessions using a SessionRegistry.
    // Parameter sessionAuthenticationStrategy the SessionAuthenticationStrategy used for handling an OAuth2AuthorizationCodeRequestAuthenticationToken
    public void setSessionAuthenticationStrategy(SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        Assert.notNull(sessionAuthenticationStrategy,
                "sessionAuthenticationStrategy 不能为空");
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }
}
