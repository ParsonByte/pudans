package cn.tolim.pudans.iam.core.auth.ticket;

import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueResponse;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.session.AppSessionEntity;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.communal.session.SsoTicketEntity;
import cn.tolim.pudans.iam.communal.session.SsoTicketRepository;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditService;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditType;
import cn.tolim.pudans.iam.core.auth.exception.AppUnavailableException;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.exception.TicketInvalidException;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.session.SessionTokenGenerator;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class SsoTicketService {

    private final SsoTicketRepository ssoTicketRepository;
    private final GlobalSessionService globalSessionService;
    private final ApplicationAccessService applicationAccessService;
    private final TicketTargetValidator ticketTargetValidator;
    private final UserAccountService userAccountService;
    private final SessionTokenGenerator tokenGenerator;
    private final IamAuthProperties properties;
    private final AuthAuditService authAuditService;

    public SsoTicketService(SsoTicketRepository ssoTicketRepository,
                            GlobalSessionService globalSessionService,
                            ApplicationAccessService applicationAccessService,
                            TicketTargetValidator ticketTargetValidator,
                            UserAccountService userAccountService,
                            SessionTokenGenerator tokenGenerator,
                            IamAuthProperties properties,
                            AuthAuditService authAuditService) {
        this.ssoTicketRepository = ssoTicketRepository;
        this.globalSessionService = globalSessionService;
        this.applicationAccessService = applicationAccessService;
        this.ticketTargetValidator = ticketTargetValidator;
        this.userAccountService = userAccountService;
        this.tokenGenerator = tokenGenerator;
        this.properties = properties;
        this.authAuditService = authAuditService;
    }

    // Issues a short lived one-time SSO ticket for an application
    // Parameter request: TicketIssueRequest
    // Returns: TicketIssueResponse
    @Transactional(rollbackFor = Exception.class)
    public TicketIssueResponse issue(TicketIssueRequest request) {
        if (request == null || request.getAppCode() == null || request.getAppCode().isBlank()) {
            throw new InvalidAuthRequestException("app_code_missing", "appCode must not be blank");
        }
        LocalDateTime now = LocalDateTime.now();
        LoginSessionEntity session;
        AppEntity app;
        String target;
        try {
            session = globalSessionService.requireActive(request.getSid());
            app = applicationAccessService.requireTicketReadyApp(request.getAppCode());
            applicationAccessService.assertCanAccess(session, app);
            target = ticketTargetValidator.validate(app, request.getTargetUrl());
        } catch (RuntimeException exception) {
            authAuditService.record(AuthAuditType.TICKET_ISSUED, null, null, request.getAppCode(),
                    request.getSid(), null, null, false, exception.getMessage());
            throw exception;
        }
        LocalDateTime expiresAt = now.plus(ticketTtl());
        SsoTicketEntity ticket = new SsoTicketEntity();
        ticket.setTicket(tokenGenerator.generate());
        ticket.setAppId(app.getId());
        ticket.setAppCode(app.getCode());
        ticket.setUserId(session.getUserId());
        ticket.setSid(session.getSid());
        ticket.setTargetUrl(target);
        ticket.setCreatedAt(now);
        ticket.setExpiresAt(expiresAt);
        ssoTicketRepository.save(ticket);
        authAuditService.record(AuthAuditType.TICKET_ISSUED, session.getUserId(), session.getUserType(),
                app.getCode(), session.getSid(), null, null, true, null);
        return TicketIssueResponse.builder()
                .ticket(ticket.getTicket())
                .appCode(app.getCode())
                .targetUrl(target)
                .nonce(request.getNonce())
                .expiresAt(expiresAt)
                .build();
    }

    // Exchanges a one-time SSO ticket for user information and an application session record
    // Parameter request: TicketExchangeRequest
    // Returns: TicketExchangeResponse
    @Transactional(rollbackFor = Exception.class)
    public TicketExchangeResponse exchange(TicketExchangeRequest request) {
        if (request == null || request.getTicket() == null || request.getTicket().isBlank()) {
            throw new InvalidAuthRequestException("ticket_missing", "ticket must not be blank");
        }
        LocalDateTime now = LocalDateTime.now();
        SsoTicketEntity ticket = ssoTicketRepository.findByTicketAndUsedAtIsNull(request.getTicket())
                .orElseThrow(() -> new TicketInvalidException("Ticket is unknown, expired or already used"));
        if (!ticket.isExchangeableAt(now)) {
            throw new TicketInvalidException("Ticket is expired");
        }
        if (request.getAppCode() != null && !request.getAppCode().isBlank()
                && !request.getAppCode().equals(ticket.getAppCode())) {
            throw new TicketInvalidException("Ticket audience does not match the application");
        }
        AppEntity app = applicationAccessService.requireTicketReadyApp(ticket.getAppCode());
        validateAppCredentials(app, request);
        LoginSessionEntity session = globalSessionService.findActive(ticket.getSid())
                .orElseThrow(() -> new TicketInvalidException("Global login session is no longer active"));
        int updated = ssoTicketRepository.markUsed(ticket.getTicket(), now);
        if (updated == 0) {
            throw new TicketInvalidException("Ticket was already used");
        }
        AppSessionEntity appSession = globalSessionService.registerApplicationSession(session.getSid(),
                app.getId(), app.getCode(), session.getUserId(), session.getExpiresAt());
        authAuditService.record(AuthAuditType.TICKET_EXCHANGED, session.getUserId(), session.getUserType(),
                app.getCode(), session.getSid(), null, null, true, null);
        return TicketExchangeResponse.builder()
                .sid(session.getSid())
                .appCode(app.getCode())
                .userId(session.getUserId())
                .sessionExpiresAt(session.getExpiresAt())
                .user(userAccountService.loadUserInfo(session))
                .build();
    }

    // Deletes expired tickets
    // Returns: int
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpired() {
        return ssoTicketRepository.deleteExpired(LocalDateTime.now());
    }

    private void validateAppCredentials(AppEntity app, TicketExchangeRequest request) {
        if (app.getClientId() != null && !app.getClientId().isBlank()) {
            if (request.getClientId() == null || request.getClientId().isBlank()
                    || !tokenGenerator.constantTimeEquals(app.getClientId(), request.getClientId())) {
                throw new AppUnavailableException("app_credentials_invalid",
                        "Application credentials are invalid");
            }
        }
        if (app.getClientSecret() != null && !app.getClientSecret().isBlank()) {
            if (request.getClientSecret() == null || request.getClientSecret().isBlank()
                    || !tokenGenerator.constantTimeEquals(app.getClientSecret(), request.getClientSecret())) {
                throw new AppUnavailableException("app_credentials_invalid",
                        "Application credentials are invalid");
            }
        }
    }

    private Duration ticketTtl() {
        Duration ttl = properties.getTicket().getTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(60) : ttl;
    }
}