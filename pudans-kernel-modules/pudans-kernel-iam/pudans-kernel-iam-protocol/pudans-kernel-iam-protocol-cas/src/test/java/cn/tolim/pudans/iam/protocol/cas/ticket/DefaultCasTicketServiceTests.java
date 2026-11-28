package cn.tolim.pudans.iam.protocol.cas.ticket;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.protocol.cas.application.CasApplicationService;
import cn.tolim.pudans.iam.protocol.cas.exception.CasErrorCodes;
import cn.tolim.pudans.iam.protocol.cas.exception.CasProtocolException;
import cn.tolim.pudans.iam.protocol.cas.model.CasProtocolConfig;
import cn.tolim.pudans.iam.protocol.cas.model.CasServiceValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class DefaultCasTicketServiceTests {

    private static final String APP_CODE = "cas-app";
    private static final String SERVICE_URL = "https://app.test/callback";

    private final ApplicationServiceLoader applicationServiceLoader =
            mock(ApplicationServiceLoader.class);
    private final CasApplicationService applicationService = mock(CasApplicationService.class);
    private final InMemoryCasTicketRegistry registry = new InMemoryCasTicketRegistry();
    private final DefaultCasTicketService ticketService = new DefaultCasTicketService(registry,
            applicationServiceLoader);

    private CasProtocolConfig config;

    @BeforeEach
    void setUp() {
        config = CasProtocolConfig.builder()
                .appCode(APP_CODE)
                .serviceUrls(Set.of(SERVICE_URL))
                .ticketTimeToLive(Duration.ofSeconds(60))
                .ticketGrantingTicketTimeToLive(Duration.ofSeconds(600))
                .renewEnabled(false)
                .build();
        when(applicationServiceLoader.getApplicationServiceByAppCode(APP_CODE))
                .thenReturn(applicationService);
        when(applicationService.getProtocolConfig(APP_CODE)).thenReturn(config);
    }

    private static UserDetails user() {
        UserDetails user = new UserDetails("user-1", "alice", UserType.USER, true, true, true,
                true, List.of());
        user.setEmail("alice@example.test");
        return user;
    }

    @Test
    void createsTicketGrantingTicketAndServiceTicket() {
        String tgt = ticketService.createTicketGrantingTicket(APP_CODE, user());
        String st = ticketService.grantServiceTicket(APP_CODE, tgt, SERVICE_URL, false);

        assertThat(tgt).startsWith("TGT-");
        assertThat(st).startsWith("ST-");
        assertThat(ticketService.getTicketGrantingTicket(tgt)).isNotNull();
    }

    @Test
    void validatesServiceTicketOnce() {
        String tgt = ticketService.createTicketGrantingTicket(APP_CODE, user());
        String st = ticketService.grantServiceTicket(APP_CODE, tgt, SERVICE_URL, false);

        CasServiceValidationResult first = ticketService.validateServiceTicket(APP_CODE,
                SERVICE_URL, st);
        CasServiceValidationResult replay = ticketService.validateServiceTicket(APP_CODE,
                SERVICE_URL, st);

        assertThat(first.isSuccess()).isTrue();
        assertThat(first.getUsername()).isEqualTo("alice");
        assertThat(first.getAttributes()).containsEntry("email", "alice@example.test");
        assertThat(replay.isSuccess()).isFalse();
        assertThat(replay.getFailureCode()).isEqualTo(CasErrorCodes.INVALID_TICKET);
    }

    @Test
    void rejectsServiceUrlThatDoesNotMatchTheTicket() {
        String tgt = ticketService.createTicketGrantingTicket(APP_CODE, user());
        String st = ticketService.grantServiceTicket(APP_CODE, tgt, SERVICE_URL, false);

        CasServiceValidationResult result = ticketService.validateServiceTicket(APP_CODE,
                "https://evil.test/callback", st);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(CasErrorCodes.INVALID_SERVICE);
    }

    @Test
    void rejectsServiceUrlPrefixOfTheTicketService() {
        String tgt = ticketService.createTicketGrantingTicket(APP_CODE, user());
        String st = ticketService.grantServiceTicket(APP_CODE, tgt, SERVICE_URL, false);

        CasServiceValidationResult result = ticketService.validateServiceTicket(APP_CODE,
                SERVICE_URL + "/nested", st);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureCode()).isEqualTo(CasErrorCodes.INVALID_SERVICE);
    }

    @Test
    void rejectsRenewWhenNotEnabled() {
        String tgt = ticketService.createTicketGrantingTicket(APP_CODE, user());

        CasProtocolException exception = assertThrows(CasProtocolException.class,
                () -> ticketService.grantServiceTicket(APP_CODE, tgt, SERVICE_URL, true));

        assertThat(exception.getErrorCode()).isEqualTo(CasErrorCodes.INVALID_REQUEST);
    }

    @Test
    void rejectsUnknownTicketGrantingTicket() {
        CasProtocolException exception = assertThrows(CasProtocolException.class,
                () -> ticketService.grantServiceTicket(APP_CODE, "TGT-missing", SERVICE_URL,
                        false));

        assertThat(exception.getErrorCode()).isEqualTo(CasErrorCodes.INVALID_TICKET);
    }

    @Test
    void destroysTicketGrantingTicket() {
        String tgt = ticketService.createTicketGrantingTicket(APP_CODE, user());

        ticketService.destroyTicketGrantingTicket(tgt);

        assertThat(ticketService.getTicketGrantingTicket(tgt)).isNull();
    }
}