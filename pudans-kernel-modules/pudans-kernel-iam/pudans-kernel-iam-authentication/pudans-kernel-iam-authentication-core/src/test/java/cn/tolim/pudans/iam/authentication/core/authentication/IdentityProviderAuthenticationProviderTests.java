package cn.tolim.pudans.iam.authentication.core.authentication;

import cn.tolim.pudans.iam.authentication.core.IdentityProviderAuthenticationService;
import cn.tolim.pudans.iam.authentication.core.IdentityProviderType;
import cn.tolim.pudans.iam.authentication.core.exception.IdentityProviderUserExistBindException;
import cn.tolim.pudans.iam.authentication.core.exception.UserBindIdentityProviderException;
import cn.tolim.pudans.iam.infra.enums.SecretType;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.infra.util.AesUtils;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdentityProviderAuthenticationProviderTests {

    private UserDetailsService userDetailsService;
    private IdentityProviderAuthenticationService identityProviderAuthenticationService;
    private PasswordEncoder passwordEncoder;
    private IdentityProviderAuthenticationProvider provider;
    private IdentityProviderUserDetails identityProviderUserDetails;
    private UserDetails userDetails;
    private String aesKey;

    @BeforeEach
    void setUp() {
        userDetailsService = mock(UserDetailsService.class);
        identityProviderAuthenticationService = mock(IdentityProviderAuthenticationService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        provider = new IdentityProviderAuthenticationProvider(userDetailsService,
                identityProviderAuthenticationService, passwordEncoder);
        identityProviderUserDetails = IdentityProviderUserDetails.builder()
                .openId("open-1")
                .providerId("provider-1")
                .providerCode("feishu")
                .providerType(IdentityProviderType.FEISHU_OAUTH)
                .nickName("Alice")
                .build();
        userDetails = new UserDetails("user-1", "alice", "stored-password",
                UserType.USER, true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        aesKey = AesUtils.generateKey();
        MockHttpServletRequest request = new MockHttpServletRequest();
        HttpSession session = request.getSession();
        session.setAttribute(SecretType.LOGIN.getKey(), aesKey);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new IdentityProviderNotBindAuthentication(identityProviderUserDetails));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private String encrypt(String value) {
        return AesUtils.encrypt(value, aesKey);
    }

    @Test
    void shouldOnlySupportIdentityProviderBindAuthentication() {
        assertTrue(provider.supports(IdentityProviderBindAuthentication.class));
        assertFalse(provider.supports(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void shouldBindIdentityProviderUserAfterPasswordVerification() {
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(passwordEncoder.matches("secret", "stored-password")).thenReturn(true);
        when(identityProviderAuthenticationService.checkIdpUserIsExistBind("open-1", "provider-1"))
                .thenReturn(false);
        when(identityProviderAuthenticationService.bindUserIdp("user-1", identityProviderUserDetails))
                .thenReturn(true);

        Authentication result = provider.authenticate(
                new IdentityProviderBindAuthentication("alice", encrypt("secret")));

        IdentityProviderAuthentication authentication =
                assertInstanceOf(IdentityProviderAuthentication.class, result);
        assertTrue(authentication.isAuthenticated());
        assertSame(userDetails, authentication.getPrincipal());
        assertEquals(IdentityProviderType.FEISHU_OAUTH, authentication.getProviderType());
        assertEquals("provider-1", authentication.getProviderId());
        assertEquals("", authentication.getCredentials());
    }

    @Test
    void shouldRejectBlankCredentialsBeforeDecryption() {
        assertThrows(ConstraintViolationException.class,
                () -> provider.authenticate(new IdentityProviderBindAuthentication(" ", "")));
    }

    @Test
    void shouldRejectWhenPasswordDoesNotMatch() {
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(passwordEncoder.matches("secret", "stored-password")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(
                        new IdentityProviderBindAuthentication("alice", encrypt("secret"))));
    }

    @Test
    void shouldRejectUnknownUser() {
        when(userDetailsService.loadUserByUsername("alice"))
                .thenThrow(new UsernameNotFoundException("missing"));

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(
                        new IdentityProviderBindAuthentication("alice", encrypt("secret"))));
    }

    @Test
    void shouldRejectWhenIdentityProviderUserIsAlreadyBound() {
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(passwordEncoder.matches("secret", "stored-password")).thenReturn(true);
        when(identityProviderAuthenticationService.checkIdpUserIsExistBind("open-1", "provider-1"))
                .thenReturn(true);

        assertThrows(IdentityProviderUserExistBindException.class,
                () -> provider.authenticate(
                        new IdentityProviderBindAuthentication("alice", encrypt("secret"))));
    }

    @Test
    void shouldRejectWhenBindingFails() {
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(passwordEncoder.matches("secret", "stored-password")).thenReturn(true);
        when(identityProviderAuthenticationService.checkIdpUserIsExistBind("open-1", "provider-1"))
                .thenReturn(false);
        when(identityProviderAuthenticationService.bindUserIdp("user-1", identityProviderUserDetails))
                .thenReturn(false);

        assertThrows(UserBindIdentityProviderException.class,
                () -> provider.authenticate(
                        new IdentityProviderBindAuthentication("alice", encrypt("secret"))));
    }

    @Test
    void shouldRejectWhenPasswordCannotBeDecrypted() {
        HttpSession session = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest().getSession();
        session.removeAttribute(SecretType.LOGIN.getKey());

        assertThrows(UserBindIdentityProviderException.class,
                () -> provider.authenticate(
                        new IdentityProviderBindAuthentication("alice", "ciphertext")));
    }

    @Test
    void shouldWrapUnexpectedUserDetailsFailure() {
        when(userDetailsService.loadUserByUsername("alice"))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(InternalAuthenticationServiceException.class,
                () -> provider.authenticate(
                        new IdentityProviderBindAuthentication("alice", encrypt("secret"))));
    }

    @Test
    void shouldRejectNullLoadedUser() {
        when(userDetailsService.loadUserByUsername(anyString())).thenReturn(null);

        assertThrows(InternalAuthenticationServiceException.class,
                () -> provider.authenticate(
                        new IdentityProviderBindAuthentication("alice", encrypt("secret"))));
    }
}