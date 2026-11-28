package cn.tolim.pudans.iam.core.auth.ticket;

import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketExchangeResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueRequest;
import cn.tolim.pudans.contract.iam.dto.ticket.TicketIssueResponse;
import cn.tolim.pudans.contract.iam.dto.ticket.UserInfo;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.session.AppSessionEntity;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.communal.session.SsoTicketEntity;
import cn.tolim.pudans.iam.communal.session.SsoTicketRepository;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.audit.AuthAuditService;
import cn.tolim.pudans.iam.core.auth.exception.TicketInvalidException;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.core.auth.session.SessionTokenGenerator;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoTicketServiceTests {

    @Mock
    private SsoTicketRepository ssoTicketRepository;

    @Mock
    private GlobalSessionService globalSessionService;

    @Mock
    private ApplicationAccessService applicationAccessService;

    @Mock
    private TicketTargetValidator ticketTargetValidator;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private AuthAuditService authAuditService;

    private SsoTicketService service;

    @BeforeEach
    void setUp() {
        service = new SsoTicketService(ssoTicketRepository, globalSessionService,
                applicationAccessService, ticketTargetValidator, userAccountService,
                new SessionTokenGenerator(), new IamAuthProperties(), authAuditService);
    }

    @Test
    void issueStoresOneTimeTicket() {
        LoginSessionEntity session = session();
        AppEntity app = app();
        when(globalSessionService.requireActive("sid-1")).thenReturn(session);
        when(applicationAccessService.requireTicketReadyApp("order")).thenReturn(app);
        when(ticketTargetValidator.validate(app, "https://order.example.com/")).thenReturn(
                "https://order.example.com/");
        when(ssoTicketRepository.save(any(SsoTicketEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        TicketIssueResponse response = service.issue(issueRequest());

        assertThat(response.getTicket()).isNotBlank();
        assertThat(response.getAppCode()).isEqualTo("order");
        assertThat(response.getTargetUrl()).isEqualTo("https://order.example.com/");
        assertThat(response.getExpiresAt()).isBefore(LocalDateTime.now().plusSeconds(61));
        verify(applicationAccessService).assertCanAccess(session, app);
    }

    @Test
    void issueRejectsDisabledApplication() {
        when(globalSessionService.requireActive("sid-1")).thenReturn(session());
        when(applicationAccessService.requireTicketReadyApp("order")).thenThrow(
                new cn.tolim.pudans.iam.core.auth.exception.AppUnavailableException("order"));
        assertThatThrownBy(() -> service.issue(issueRequest()))
                .isInstanceOf(cn.tolim.pudans.iam.core.auth.exception.AppUnavailableException.class);
        verify(ssoTicketRepository, never()).save(any());
    }

    @Test
    void exchangeRejectsUsedTicket() {
        when(ssoTicketRepository.findByTicketAndUsedAtIsNull("ticket-1"))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.exchange(exchangeRequest()))
                .isInstanceOf(TicketInvalidException.class);
    }

    @Test
    void exchangeRejectsReplayWhenTicketIsMarkedUsed() {
        SsoTicketEntity ticket = ticket("ticket-1");
        when(ssoTicketRepository.findByTicketAndUsedAtIsNull("ticket-1"))
                .thenReturn(Optional.of(ticket));
        when(applicationAccessService.requireTicketReadyApp("order")).thenReturn(app());
        when(globalSessionService.findActive("sid-1")).thenReturn(Optional.of(session()));
        when(ssoTicketRepository.markUsed(anyString(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.exchange(exchangeRequest()))
                .isInstanceOf(TicketInvalidException.class)
                .hasMessageContaining("already used");
    }

    @Test
    void exchangeRegistersApplicationSession() {
        SsoTicketEntity ticket = ticket("ticket-1");
        LoginSessionEntity session = session();
        AppEntity app = app();
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId("user-1");
        when(ssoTicketRepository.findByTicketAndUsedAtIsNull("ticket-1"))
                .thenReturn(Optional.of(ticket));
        when(applicationAccessService.requireTicketReadyApp("order")).thenReturn(app);
        when(globalSessionService.findActive("sid-1")).thenReturn(Optional.of(session));
        when(ssoTicketRepository.markUsed(anyString(), any())).thenReturn(1);
        when(globalSessionService.registerApplicationSession("sid-1", "app-1", "order", "user-1",
                session.getExpiresAt())).thenReturn(new AppSessionEntity());
        when(userAccountService.loadUserInfo(session)).thenReturn(userInfo);

        TicketExchangeResponse response = service.exchange(exchangeRequest());

        assertThat(response.getSid()).isEqualTo("sid-1");
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getUser()).isSameAs(userInfo);
        verify(ssoTicketRepository).markUsed(anyString(), any());
    }

    @Test
    void exchangeRejectsWrongClientSecret() {
        SsoTicketEntity ticket = ticket("ticket-1");
        when(ssoTicketRepository.findByTicketAndUsedAtIsNull("ticket-1"))
                .thenReturn(Optional.of(ticket));
        when(applicationAccessService.requireTicketReadyApp("order")).thenReturn(app());
        TicketExchangeRequest request = exchangeRequest();
        request.setClientSecret("wrong");
        assertThatThrownBy(() -> service.exchange(request))
                .isInstanceOf(cn.tolim.pudans.iam.core.auth.exception.AppUnavailableException.class);
    }

    private TicketIssueRequest issueRequest() {
        TicketIssueRequest request = new TicketIssueRequest();
        request.setSid("sid-1");
        request.setAppCode("order");
        request.setTargetUrl("https://order.example.com/");
        return request;
    }

    private TicketExchangeRequest exchangeRequest() {
        TicketExchangeRequest request = new TicketExchangeRequest();
        request.setTicket("ticket-1");
        request.setAppCode("order");
        request.setClientId("client-1");
        request.setClientSecret("secret-1");
        return request;
    }

    private SsoTicketEntity ticket(String value) {
        SsoTicketEntity ticket = new SsoTicketEntity();
        ticket.setTicket(value);
        ticket.setAppId("app-1");
        ticket.setAppCode("order");
        ticket.setUserId("user-1");
        ticket.setSid("sid-1");
        ticket.setCreatedAt(LocalDateTime.now());
        ticket.setExpiresAt(LocalDateTime.now().plusSeconds(60));
        return ticket;
    }

    private LoginSessionEntity session() {
        LoginSessionEntity session = new LoginSessionEntity();
        session.setSid("sid-1");
        session.setUserId("user-1");
        session.setUserType("USER");
        session.setOrgId("org-1");
        session.setCreatedAt(LocalDateTime.now());
        session.setLastSeenAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setRevoked(Boolean.FALSE);
        return session;
    }

    private AppEntity app() {
        AppEntity app = new AppEntity();
        app.setId("app-1");
        app.setCode("order");
        app.setName("Order");
        app.setClientId("client-1");
        app.setClientSecret("secret-1");
        app.setEnabled(Boolean.TRUE);
        app.setConfigured(Boolean.TRUE);
        return app;
    }
}