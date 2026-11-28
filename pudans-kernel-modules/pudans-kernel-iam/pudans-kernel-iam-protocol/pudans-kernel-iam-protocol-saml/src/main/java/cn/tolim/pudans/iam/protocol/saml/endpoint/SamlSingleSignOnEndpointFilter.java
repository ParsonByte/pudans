package cn.tolim.pudans.iam.protocol.saml.endpoint;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.protocol.saml.application.SamlApplicationService;
import cn.tolim.pudans.iam.protocol.saml.constant.SamlBindingType;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlErrorCodes;
import cn.tolim.pudans.iam.protocol.saml.exception.SamlProtocolException;
import cn.tolim.pudans.iam.protocol.saml.model.SamlAuthnRequest;
import cn.tolim.pudans.iam.protocol.saml.model.SamlProtocolConfig;
import cn.tolim.pudans.iam.protocol.saml.service.SamlProtocolService;
import cn.tolim.pudans.iam.protocol.saml.store.SamlAuthnRequestStore;
import cn.tolim.pudans.iam.protocol.saml.util.SamlRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.DEFAULT_AUTHN_REQUEST_TIME_TO_LIVE_SECONDS;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.RELAY_STATE_PARAMETER;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.REQUEST_ID_PARAMETER;
import static cn.tolim.pudans.iam.protocol.saml.constant.SamlProtocolConstants.SAML_REQUEST_PARAMETER;


public final class SamlSingleSignOnEndpointFilter extends OncePerRequestFilter {

    private final RequestMatcher requestMatcher;
    private final ApplicationServiceLoader applicationServiceLoader;
    private final SamlProtocolService samlProtocolService;
    private final SamlAuthnRequestStore authnRequestStore;

    public SamlSingleSignOnEndpointFilter(RequestMatcher requestMatcher,
                                          ApplicationServiceLoader applicationServiceLoader,
                                          SamlProtocolService samlProtocolService,
                                          SamlAuthnRequestStore authnRequestStore) {
        Assert.notNull(requestMatcher, "requestMatcher must not be null");
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader must not be null");
        Assert.notNull(samlProtocolService, "samlProtocolService must not be null");
        Assert.notNull(authnRequestStore, "authnRequestStore must not be null");
        this.requestMatcher = requestMatcher;
        this.applicationServiceLoader = applicationServiceLoader;
        this.samlProtocolService = samlProtocolService;
        this.authnRequestStore = authnRequestStore;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException,
            IOException {
        if (!requestMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String appCode = SamlRequestUtils.resolveAppCode(request);
        if (appCode == null) {
            SamlResponseUtils.writeError(response, HttpStatus.NOT_FOUND.value(),
                    "Unknown SAML application");
            return;
        }
        try {
            SamlApplicationService applicationService = (SamlApplicationService)
                    applicationServiceLoader.getApplicationServiceByAppCode(appCode);
            SamlProtocolConfig config = applicationService.getProtocolConfig(appCode);
            SamlAuthnRequest authnRequest = resolveAuthnRequest(request);
            if (authnRequest == null) {
                SamlResponseUtils.writeError(response, HttpStatus.BAD_REQUEST.value(),
                        "SAMLRequest is missing");
                return;
            }
            String relayState = request.getParameter(RELAY_STATE_PARAMETER);
            if (relayState != null) {
                authnRequest.setRelayState(relayState);
            }
            validateAuthnRequest(config, authnRequest);
            if (!SecurityUtils.isAuthenticated()) {
                // Preserve the request for the post-binding round trip after login.
                authnRequestStore.save(authnRequest, Duration.ofSeconds(
                        DEFAULT_AUTHN_REQUEST_TIME_TO_LIVE_SECONDS));
                String returnUrl = SamlRequestUtils.currentRequestUrl(request);
                if (HttpMethod.POST.matches(request.getMethod())) {
                    returnUrl = SamlRequestUtils.getRequestPath(request) + "?"
                            + REQUEST_ID_PARAMETER + "=" + authnRequest.getId();
                }
                response.sendRedirect(SamlRequestUtils.buildLoginRedirect(returnUrl));
                return;
            }
            UserDetails user = SecurityUtils.getCurrentUser();
            if (user == null) {
                response.sendRedirect(SamlRequestUtils.buildLoginRedirect(
                        SamlRequestUtils.currentRequestUrl(request)));
                return;
            }
            String assertionConsumerServiceUrl = StringUtils.defaultIfBlank(
                    authnRequest.getAssertionConsumerServiceUrl(),
                    config.getAssertionConsumerServiceUrl());
            if (StringUtils.isBlank(assertionConsumerServiceUrl)) {
                SamlResponseUtils.writeError(response, HttpStatus.BAD_REQUEST.value(),
                        "Assertion consumer service URL is not configured");
                return;
            }
            String signedResponse = samlProtocolService.buildSignedResponse(config, authnRequest,
                    user);
            authnRequestStore.remove(authnRequest.getId());
            SamlResponseUtils.writePostBinding(response, assertionConsumerServiceUrl,
                    signedResponse, authnRequest.getRelayState());
        } catch (SamlProtocolException exception) {
            SamlResponseUtils.writeError(response, exception.getHttpStatus().value(),
                    exception.getMessage());
        }
    }

    // Ensure the request comes from the registered service provider and targets the registered consumer.
    private void validateAuthnRequest(SamlProtocolConfig config, SamlAuthnRequest authnRequest) {
        String registeredEntityId = normalize(config.getEntityId());
        if (registeredEntityId == null || !registeredEntityId.equals(
                normalize(authnRequest.getIssuer()))) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "AuthnRequest issuer does not match the registered service provider");
        }
        String registeredConsumerUrl = normalize(config.getAssertionConsumerServiceUrl());
        String requestedConsumerUrl = normalize(authnRequest.getAssertionConsumerServiceUrl());
        if (registeredConsumerUrl == null
                || (requestedConsumerUrl != null
                && !registeredConsumerUrl.equals(requestedConsumerUrl))) {
            throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST,
                    "AuthnRequest assertion consumer service URL is not registered");
        }
    }

    private String normalize(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private SamlAuthnRequest resolveAuthnRequest(HttpServletRequest request) {
        String requestId = request.getParameter(REQUEST_ID_PARAMETER);
        if (StringUtils.isNotBlank(requestId)) {
            SamlAuthnRequest stored = authnRequestStore.consume(requestId);
            if (stored == null) {
                throw new SamlProtocolException(SamlErrorCodes.INVALID_REQUEST_ID,
                        "SAML request expired or already used");
            }
            return stored;
        }
        String samlRequest = request.getParameter(SAML_REQUEST_PARAMETER);
        if (StringUtils.isBlank(samlRequest)) {
            return null;
        }
        SamlBindingType binding = HttpMethod.POST.matches(request.getMethod())
                ? SamlBindingType.POST : SamlBindingType.REDIRECT;
        return samlProtocolService.parseAuthnRequest(samlRequest, binding);
    }
}