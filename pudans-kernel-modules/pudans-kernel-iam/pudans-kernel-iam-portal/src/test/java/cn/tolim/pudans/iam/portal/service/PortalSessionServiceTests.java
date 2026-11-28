package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.communal.core.security.AuthenticatedUser;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationEntity;
import cn.tolim.pudans.iam.communal.entity.account.OrganizationMemberEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupEntity;
import cn.tolim.pudans.iam.communal.entity.account.UserGroupMemberEntity;
import cn.tolim.pudans.iam.communal.enums.UserStatus;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.OrganizationRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupMemberRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserGroupRepository;
import cn.tolim.pudans.iam.communal.repository.account.UserRepository;
import cn.tolim.pudans.iam.communal.repository.setting.AdministratorRepository;
import cn.tolim.pudans.iam.communal.session.LoginSessionEntity;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.IamAuthTypes;
import cn.tolim.pudans.iam.core.auth.session.GlobalSessionService;
import cn.tolim.pudans.iam.portal.constant.PortalConstants;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortalSessionServiceTests {

    private static final String USER_ID = "user-1";
    private static final String ORGANIZATION_ID = "org-1";

    private final GlobalSessionService globalSessionService = mock(GlobalSessionService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AdministratorRepository administratorRepository = mock(AdministratorRepository.class);
    private final UserGroupMemberRepository userGroupMemberRepository = mock(UserGroupMemberRepository.class);
    private final UserGroupRepository userGroupRepository = mock(UserGroupRepository.class);
    private final OrganizationMemberRepository organizationMemberRepository =
            mock(OrganizationMemberRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<IamAuthProperties> authPropertiesProvider = mock(ObjectProvider.class);

    private final PortalSessionService service = new PortalSessionService(globalSessionService,
            userRepository, administratorRepository, userGroupMemberRepository, userGroupRepository,
            organizationMemberRepository, organizationRepository, authPropertiesProvider);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesIdentityFromSessionHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IamAuthTypes.SESSION_HEADER, "sid-header");
        when(globalSessionService.findActive("sid-header"))
                .thenReturn(Optional.of(session("sid-header", USER_ID, IamAuthTypes.USER)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        stubMemberships();

        PortalIdentity identity = service.resolve(request).orElseThrow();

        assertThat(identity.userId()).isEqualTo(USER_ID);
        assertThat(identity.username()).isEqualTo("alice");
        assertThat(identity.displayName()).isEqualTo("Alice Example");
        assertThat(identity.userType()).isEqualTo(IamAuthTypes.USER);
        assertThat(identity.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(identity.organizationName()).isEqualTo("Default organization");
        assertThat(identity.groups()).containsExactly("ops");
        assertThat(identity.session()).isNotNull();
        assertThat(identity.session().getActive()).isTrue();
    }

    @Test
    void resolvesIdentityFromSessionCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie(PortalConstants.DEFAULT_SESSION_COOKIE_NAME, "sid-cookie"));
        when(authPropertiesProvider.getIfAvailable()).thenReturn(null);
        when(globalSessionService.findActive("sid-cookie"))
                .thenReturn(Optional.of(session("sid-cookie", USER_ID, IamAuthTypes.USER)));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        stubMemberships();

        PortalIdentity identity = service.resolve(request).orElseThrow();

        assertThat(identity.userId()).isEqualTo(USER_ID);
        assertThat(identity.session().getLoginIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void resolvesIdentityFromAuthenticatedSessionAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(IamAuthTypes.SESSION_ATTRIBUTE, session("sid-attribute", USER_ID,
                IamAuthTypes.USER));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        stubMemberships();

        PortalIdentity identity = service.resolve(request).orElseThrow();

        assertThat(identity.userId()).isEqualTo(USER_ID);
        assertThat(identity.session()).isNotNull();
        assertThat(identity.session().getExpiresAt()).isNotNull();
        verify(globalSessionService, never()).findActive(any());
    }

    @Test
    void ignoresInactiveSessionAttribute() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        LoginSessionEntity inactive = session("sid-attribute", USER_ID, IamAuthTypes.USER);
        inactive.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        request.setAttribute(IamAuthTypes.SESSION_ATTRIBUTE, inactive);

        assertThat(service.resolve(request)).isEmpty();
        verify(globalSessionService, never()).findActive(any());
    }

    @Test
    void ignoresInactiveSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IamAuthTypes.SESSION_HEADER, "revoked");
        when(globalSessionService.findActive("revoked")).thenReturn(Optional.empty());

        assertThat(service.resolve(request)).isEmpty();
        verify(userRepository, never()).findById(any());
    }

    @Test
    void rejectsDisabledUserOfActiveSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IamAuthTypes.SESSION_HEADER, "sid-disabled");
        when(globalSessionService.findActive("sid-disabled"))
                .thenReturn(Optional.of(session("sid-disabled", USER_ID, IamAuthTypes.USER)));
        UserEntity user = user(USER_ID);
        user.setStatus(UserStatus.DISABLED);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.resolve(request)).isEmpty();
    }

    @Test
    void rejectsExpiredUserOfActiveSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(IamAuthTypes.SESSION_HEADER, "sid-expired-user");
        when(globalSessionService.findActive("sid-expired-user"))
                .thenReturn(Optional.of(session("sid-expired-user", USER_ID, IamAuthTypes.USER)));
        UserEntity user = user(USER_ID);
        user.setExpireDate(LocalDate.now().minusDays(1));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.resolve(request)).isEmpty();
    }

    @Test
    void fallsBackToUpstreamAuthenticatedUser() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(USER_ID, "alice", "Alice",
                ORGANIZATION_ID);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        stubMemberships();

        PortalIdentity identity = service.resolve(new MockHttpServletRequest()).orElseThrow();

        assertThat(identity.userId()).isEqualTo(USER_ID);
        assertThat(identity.organizationId()).isEqualTo(ORGANIZATION_ID);
        assertThat(identity.session()).isNull();
    }

    @Test
    void fallsBackToUpstreamTokenWhenKernelUserIsMissing() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("token-user", "bob", "Bob",
                null);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(authenticatedUser, null, List.of()));
        when(userRepository.findById("token-user")).thenReturn(Optional.empty());

        PortalIdentity identity = service.resolve(new MockHttpServletRequest()).orElseThrow();

        assertThat(identity.userId()).isEqualTo("token-user");
        assertThat(identity.displayName()).isEqualTo("Bob");
        assertThat(identity.groups()).isEmpty();
    }

    @Test
    void returnsEmptyWhenAnonymous() {
        assertThat(service.resolve(new MockHttpServletRequest())).isEmpty();
    }

    private void stubMemberships() {
        UserGroupMemberEntity member = new UserGroupMemberEntity();
        member.setUserId(USER_ID);
        member.setGroupId("group-1");
        when(userGroupMemberRepository.findByUserId(USER_ID)).thenReturn(List.of(member));
        UserGroupEntity group = new UserGroupEntity();
        group.setId("group-1");
        group.setCode("ops");
        when(userGroupRepository.findAllById(List.of("group-1"))).thenReturn(List.of(group));
        OrganizationMemberEntity organizationMember = new OrganizationMemberEntity(ORGANIZATION_ID,
                USER_ID);
        when(organizationMemberRepository.findAllByUserId(USER_ID))
                .thenReturn(List.of(organizationMember));
        OrganizationEntity organization = new OrganizationEntity();
        organization.setId(ORGANIZATION_ID);
        organization.setName("Default organization");
        when(organizationRepository.findById(ORGANIZATION_ID)).thenReturn(Optional.of(organization));
    }

    private static UserEntity user(String userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setUsername("alice");
        user.setFullName("Alice Example");
        user.setEmail("alice@example.test");
        user.setPhone("13800000000");
        user.setStatus(UserStatus.ENABLED);
        return user;
    }

    private static LoginSessionEntity session(String sid, String userId, String userType) {
        LoginSessionEntity session = new LoginSessionEntity();
        session.setSid(sid);
        session.setUserId(userId);
        session.setUserType(userType);
        session.setOrgId(ORGANIZATION_ID);
        session.setCreatedAt(LocalDateTime.now().minusMinutes(1));
        session.setLastSeenAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusHours(1));
        session.setRevoked(Boolean.FALSE);
        session.setIp("10.0.0.1");
        session.setUserAgent("JUnit");
        return session;
    }
}