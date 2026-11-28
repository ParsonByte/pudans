package cn.tolim.pudans.iam.protocol.cas.endpoint;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.protocol.cas.application.CasApplicationService;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;
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

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.SERVICE_PARAMETER;


public final class CasLogoutEndpointFilter extends OncePerRequestFilter {

    private final RequestMatcher requestMatcher;
    private final ApplicationServiceLoader applicationServiceLoader;
    private final CasTicketService casTicketService;

    public CasLogoutEndpointFilter(RequestMatcher requestMatcher,
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
        CasApplicationService applicationService = (CasApplicationService)
                applicationServiceLoader.getApplicationServiceByAppCode(appCode);
        CasProtocolConfig config = applicationService.getProtocolConfig(appCode);
        String ticketGrantingTicketId = findTicketGrantingTicketId(request,
                config.getCookieName());
        if (ticketGrantingTicketId != null) {
            casTicketService.destroyTicketGrantingTicket(ticketGrantingTicketId);
        }
        expireCookie(response, config.getCookieName());
        String serviceUrl = request.getParameter(SERVICE_PARAMETER);
        if (StringUtils.isNotBlank(serviceUrl)
                && CasServiceUrlValidator.isAllowed(config.getServiceUrls(), serviceUrl)) {
            response.sendRedirect(serviceUrl);
            return;
        }
        CasValidationResponseWriter.writeTextError(response, HttpStatus.OK.value(),
                "Logout successful");
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

    private void expireCookie(HttpServletResponse response, String cookieName) {
        Cookie cookie = new Cookie(cookieName, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}