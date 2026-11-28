package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.session.BackchannelLogoutRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueResponse;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.contract.dto.IamTicketIssueRequest;
import cn.tolim.pudans.iam.core.auth.session.SessionManagementService;
import cn.tolim.pudans.iam.core.auth.ticket.SsoTicketService;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "IAM 单点登录")
@RestController
public class IamSsoEndpoint {

    private final SsoTicketService ssoTicketService;
    private final SessionManagementService sessionManagementService;
    private final IamSessionCookieService cookieService;

    public IamSsoEndpoint(SsoTicketService ssoTicketService,
                          SessionManagementService sessionManagementService,
                          IamSessionCookieService cookieService) {
        this.ssoTicketService = ssoTicketService;
        this.sessionManagementService = sessionManagementService;
        this.cookieService = cookieService;
    }

    // Issues a one-time SSO ticket for an application
    // Parameter request: IamTicketIssueRequest
    // Parameter httpRequest: HttpServletRequest
    // Returns: TicketIssueResponse
    @Operation(summary = "签发票据")
    @PostMapping(path = {IamAuthApiPaths.TICKET_ISSUE, IamAuthApiPaths.TICKET_ISSUE_PLURAL})
    public TicketIssueResponse issue(@RequestBody IamTicketIssueRequest request,
                                     HttpServletRequest httpRequest) {
        TicketIssueRequest ticketRequest = new TicketIssueRequest();
        ticketRequest.setSid(request == null ? null : resolveSid(request.getSid(), httpRequest));
        ticketRequest.setAppCode(request == null ? null : request.getAppCode());
        ticketRequest.setTargetUrl(request == null ? null : request.resolvedTargetUrl());
        ticketRequest.setNonce(request == null ? null : request.getNonce());
        return ssoTicketService.issue(ticketRequest);
    }

    // Exchanges a one-time SSO ticket with application credentials
    // Parameter request: TicketExchangeRequest
    // Returns: TicketExchangeResponse
    @Operation(summary = "兑换票据")
    @PostMapping(path = {IamAuthApiPaths.TICKET_EXCHANGE, IamAuthApiPaths.TICKET_EXCHANGE_PLURAL})
    public TicketExchangeResponse exchange(@RequestBody TicketExchangeRequest request) {
        return ssoTicketService.exchange(request);
    }

    // Dispatches back-channel logout notifications for a session or a user
    // Parameter request: BackchannelLogoutRequest
    // Returns: LogoutResponse
    @Operation(summary = "处理后端登出")
    @PostMapping(path = IamAuthApiPaths.BACKCHANNEL_LOGOUT)
    public cn.tolim.pudans.contract.iam.dto.auth.LogoutResponse backchannelLogout(
            @RequestBody BackchannelLogoutRequest request) {
        return sessionManagementService.dispatchBackchannelLogout(
                request == null ? null : request.getSid(),
                request == null ? null : request.getSub(),
                "backchannel_logout");
    }

    private String resolveSid(String requestSid, HttpServletRequest httpRequest) {
        if (requestSid != null && !requestSid.isBlank()) {
            return requestSid;
        }
        return cookieService.resolveSid(httpRequest);
    }
}