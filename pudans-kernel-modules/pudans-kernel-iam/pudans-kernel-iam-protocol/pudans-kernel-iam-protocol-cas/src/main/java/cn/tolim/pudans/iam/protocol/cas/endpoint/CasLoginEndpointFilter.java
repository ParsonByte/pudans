package cn.tolim.pudans.iam.protocol.cas.endpoint;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.util.SecurityUtils;
import cn.tolim.pudans.iam.protocol.cas.application.CasApplicationService;
import cn.tolim.pudans.iam.protocol.cas.exception.CasErrorCodes;
import cn.tolim.pudans.iam.protocol.cas.exception.CasProtocolException;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;
import cn.tolim.pudans.iam.protocol.cas.model.CasTicketGrantingTicket;
import cn.tolim.pudans.iam.protocol.cas.service.CasServiceUrlValidator;
import cn.tolim.pudans.iam.protocol.cas.service.CasValidationResponseWriter;
import cn.tolim.pudans.iam.protocol.cas.ticket.CasTicketService;
import cn.tolim.pudans.iam.protocol.cas.util.CasRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.RENEW_PARAMETER;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.SERVICE_PARAMETER;


public final class CasLoginEndpointFilter extends OncePerRequestFilter {

    private final RequestMatcher requestMatcher;
    private final ApplicationServiceLoader applicationServiceLoader;
    private final CasTicketService casTicketService;

    public CasLoginEndpointFilter(RequestMatcher requestMatcher,
                                  ApplicationServiceLoader applicationServiceLoader,
                                  CasTicketService casTicketService) {
        Assert.notNull(requestMatcher, "requestMatcher must not be null");
        Assert.notNull(applicationServiceLoader, "applicationServiceLoader must not be null");
        Assert.notNull(casTicketService, "casTicketService must not be null");
        this.requestMatcher = requestMatcher;
        this.applicationServiceLoader = applicationServiceLoader;
        this.casTicketService = casTicketService;
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
        String appCode = CasRequestUtils.resolveAppCode(request);
        if (appCode == null) {
            CasValidationResponseWriter.writeTextError(response, HttpStatus.NOT_FOUND.value(),
                    "Unknown CAS application");
            return;
        }
        try {
            CasApplicationService applicationService = (CasApplicationService)
                    applicationServiceLoader.getApplicationServiceByAppCode(appCode);
            CasProtocolConfig config = applicationService.getProtocolConfig(appCode);
            String serviceUrl = StringUtils.defaultIfBlank(
                    request.getParameter(SERVICE_PARAMETER), config.getDefaultServiceUrl());
            if (StringUtils.isBlank(serviceUrl)
                    || !CasServiceUrlValidator.isAllowed(config.getServiceUrls(), serviceUrl)) {
                CasValidationResponseWriter.writeTextError(response,
                        HttpStatus.BAD_REQUEST.value(),
                        CasErrorCodes.INVALID_SERVICE + ": service URL is not registered");
                return;
            }
            if (!SecurityUtils.isAuthenticated()) {
                response.sendRedirect(CasRequestUtils.buildLoginRedirect(
                        CasRequestUtils.currentRequestUrl(request)));
                return;
            }
            UserDetails user = SecurityUtils.getCurrentUser();
            if (user == null) {
                response.sendRedirect(CasRequestUtils.buildLoginRedirect(
                        CasRequestUtils.currentRequestUrl(request)));
                return;
            }
            String cookieName = config.getCookieName();
            String ticketGrantingTicketId = findTicketGrantingTicketId(request, cookieName);
            CasTicketGrantingTicket grantingTicket = ticketGrantingTicketId == null ? null
                    : casTicketService.getTicketGrantingTicket(ticketGrantingTicketId);
            if (grantingTicket == null || !appCode.equals(grantingTicket.getAppCode())) {
                ticketGrantingTicketId = casTicketService
                        .createTicketGrantingTicket(appCode, user);
                writeTicketGrantingTicketCookie(response, config, ticketGrantingTicketId);
            }
            String renewParameter = request.getParameter(RENEW_PARAMETER);
            boolean renew = renewParameter != null && !"false".equalsIgnoreCase(renewParameter);
            String serviceTicket = casTicketService.grantServiceTicket(appCode,
                    ticketGrantingTicketId, serviceUrl, renew);
            response.sendRedirect(CasRequestUtils.appendTicket(serviceUrl, serviceTicket));
        } catch (CasProtocolException exception) {
            CasValidationResponseWriter.writeTextError(response,
                    exception.getHttpStatus().value(), exception.getMessage());
        }
    }

    private String findTicketGrantingTicketId(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (Objects.equals(cookieName, cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeTicketGrantingTicketCookie(HttpServletResponse response,
                                                 CasProtocolConfig config, String value) {
        Cookie cookie = new Cookie(config.getCookieName(), value);
        cookie.setHttpOnly(true);
        cookie.setSecure(Boolean.TRUE.equals(config.getSecureCookie()));
        cookie.setPath("/");
        cookie.setMaxAge((int) config.getTicketGrantingTicketTimeToLive().toSeconds());
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}