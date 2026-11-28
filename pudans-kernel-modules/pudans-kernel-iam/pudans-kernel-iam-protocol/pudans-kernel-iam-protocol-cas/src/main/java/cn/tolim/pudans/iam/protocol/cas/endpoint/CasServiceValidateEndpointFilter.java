package cn.tolim.pudans.iam.protocol.cas.endpoint;

import cn.tolim.pudans.iam.protocol.cas.exception.CasErrorCodes;
import cn.tolim.pudans.iam.protocol.cas.model.CasServiceValidationResult;
import cn.tolim.pudans.iam.protocol.cas.service.CasValidationResponseWriter;
import cn.tolim.pudans.iam.protocol.cas.ticket.CasTicketService;
import cn.tolim.pudans.iam.protocol.cas.util.CasRequestUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.NonNull;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.Assert;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.FORMAT_PARAMETER;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.SERVICE_PARAMETER;
import static cn.tolim.pudans.iam.protocol.cas.constant.CasProtocolConstants.TICKET_PARAMETER;


public final class CasServiceValidateEndpointFilter extends OncePerRequestFilter {

    private final RequestMatcher requestMatcher;
    private final CasTicketService casTicketService;

    public CasServiceValidateEndpointFilter(RequestMatcher requestMatcher,
                                            CasTicketService casTicketService) {
        Assert.notNull(requestMatcher, "requestMatcher must not be null");
        Assert.notNull(casTicketService, "casTicketService must not be null");
        this.requestMatcher = requestMatcher;
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
            CasValidationResponseWriter.writeTextError(response,
                    HttpServletResponse.SC_NOT_FOUND, "Unknown CAS application");
            return;
        }
        String serviceUrl = request.getParameter(SERVICE_PARAMETER);
        String ticket = request.getParameter(TICKET_PARAMETER);
        if (StringUtils.isBlank(serviceUrl) || StringUtils.isBlank(ticket)) {
            CasValidationResponseWriter.writeValidationResponse(response,
                    CasServiceValidationResult.failure(CasErrorCodes.INVALID_REQUEST,
                            "Service and ticket parameters are required"), false, false);
            return;
        }
        CasServiceValidationResult result = casTicketService.validateServiceTicket(appCode,
                serviceUrl, ticket);
        boolean includeAttributes = CasRequestUtils.getRequestPath(request).contains("/p3/");
        boolean json = "json".equalsIgnoreCase(request.getParameter(FORMAT_PARAMETER));
        CasValidationResponseWriter.writeValidationResponse(response, result,
                includeAttributes, json);
    }
}