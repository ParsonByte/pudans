package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.portal.dto.PortalMeResponse;
import cn.tolim.pudans.iam.portal.dto.PortalSessionView;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalMeServiceTests {

    private final PortalSessionService portalSessionService = mock(PortalSessionService.class);
    private final PortalMeService service = new PortalMeService(portalSessionService);

    @Test
    void mapsIdentityToUserView() {
        PortalSessionView session = new PortalSessionView();
        session.setActive(Boolean.TRUE);
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        PortalIdentity identity = new PortalIdentity("user-1", "alice", "Alice Example", "USER",
                "org-1", "Default organization", "alice@example.test", "13800000000", null,
                Boolean.TRUE, Boolean.FALSE, List.of("ops"), session);
        when(portalSessionService.resolve(any())).thenReturn(Optional.of(identity));

        PortalMeResponse response = service.getCurrentUser(new MockHttpServletRequest());

        assertThat(response.getAuthenticated()).isTrue();
        assertThat(response.getUser()).isNotNull();
        assertThat(response.getUser().getUserId()).isEqualTo("user-1");
        assertThat(response.getUser().getDisplayName()).isEqualTo("Alice Example");
        assertThat(response.getUser().getGroups()).containsExactly("ops");
        assertThat(response.getSession()).isSameAs(session);
    }

    @Test
    void reportsAnonymousCaller() {
        when(portalSessionService.resolve(any())).thenReturn(Optional.empty());

        PortalMeResponse response = service.getCurrentUser(new MockHttpServletRequest());

        assertThat(response.getAuthenticated()).isFalse();
        assertThat(response.getUser()).isNull();
        assertThat(response.getSession()).isNull();
    }
}