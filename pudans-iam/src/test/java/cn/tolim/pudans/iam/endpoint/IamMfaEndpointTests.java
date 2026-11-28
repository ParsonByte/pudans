package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.MfaChallengeResponse;
import cn.tolim.pudans.iam.contract.IamApiExceptionHandler;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnAuthenticationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.service.WebAuthnService;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.exception.InvalidAuthRequestException;
import cn.tolim.pudans.iam.core.auth.mfa.MfaChallenge;
import cn.tolim.pudans.iam.core.auth.mfa.MfaMethod;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IamMfaEndpointTests {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private WebAuthnService webAuthnService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamSessionCookieService cookieService = new IamSessionCookieService(new IamAuthProperties());
        IamMfaEndpoint endpoint = new IamMfaEndpoint(authenticationService, webAuthnService,
                cookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new IamApiExceptionHandler())
                .build();
    }

    @Test
    void challengeReturnsPendingMethod() throws Exception {
        when(authenticationService.mfaChallenge(any())).thenReturn(MfaChallengeResponse.builder()
                .challengeId("challenge-1").username("alice").method(MfaMethod.TOTP.name())
                .expiresAt(LocalDateTime.now().plusMinutes(5)).build());

        mockMvc.perform(post(IamAuthApiPaths.MFA_CHALLENGE_CONTRACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value("challenge-1"))
                .andExpect(jsonPath("$.method").value("TOTP"));
    }

    @Test
    void verifyCreatesSessionCookie() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setSid("sid-mfa");
        response.setExpiresAt(LocalDateTime.now().plusHours(8));
        response.setMfaRequired(Boolean.FALSE);
        when(authenticationService.verifyMfa(any(), anyString(), any())).thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.MFA_VERIFY_CONTRACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\",\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("PUDANS_SID=sid-mfa")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(jsonPath("$.sid").value("sid-mfa"))
                .andExpect(jsonPath("$.mfaRequired").value(false));
    }

    @Test
    void verifyRejectsIncompleteRequest() throws Exception {
        when(authenticationService.verifyMfa(any(), anyString(), any()))
                .thenThrow(new InvalidAuthRequestException("mfa_verify_request_invalid",
                        "Challenge id and verification code must not be blank"));

        mockMvc.perform(post(IamAuthApiPaths.MFA_VERIFY_CONTRACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mfa_verify_request_invalid"));
    }

    @Test
    void webAuthnOptionsReturnCeremonyOptions() throws Exception {
        when(authenticationService.requireMfaChallenge(eq("challenge-1"), eq("alice"), isNull(),
                eq(MfaMethod.WEBAUTHN))).thenReturn(webAuthnChallenge());
        when(webAuthnService.authenticationOptions("user-1", "alice"))
                .thenReturn(new WebAuthnAuthenticationOptions("wa-1", "browserchallenge",
                        "localhost", 60000L, "preferred", List.of()));

        mockMvc.perform(post(IamAuthApiPaths.MFA_WEBAUTHN_OPTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\",\"username\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value("wa-1"))
                .andExpect(jsonPath("$.challenge").value("browserchallenge"))
                .andExpect(jsonPath("$.rpId").value("localhost"));
    }

    @Test
    void webAuthnVerifyCreatesSessionCookie() throws Exception {
        when(authenticationService.requireMfaChallenge(eq("challenge-1"), eq("alice"), isNull(),
                eq(MfaMethod.WEBAUTHN))).thenReturn(webAuthnChallenge());
        when(webAuthnService.completeAuthentication(eq("user-1"), any())).thenReturn(null);
        LoginResponse response = new LoginResponse();
        response.setSid("sid-webauthn");
        response.setExpiresAt(LocalDateTime.now().plusHours(8));
        when(authenticationService.completeMfaLogin(eq("challenge-1"), anyString(), any()))
                .thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.MFA_WEBAUTHN_VERIFY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assertionBody()))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        containsString("PUDANS_SID=sid-webauthn")))
                .andExpect(jsonPath("$.sid").value("sid-webauthn"));
    }

    @Test
    void webAuthnVerifyRejectsIncompleteAssertion() throws Exception {
        mockMvc.perform(post(IamAuthApiPaths.MFA_WEBAUTHN_VERIFY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("mfa_webauthn_request_invalid"));

        verify(webAuthnService, never()).completeAuthentication(any(), any());
    }

    private MfaChallenge webAuthnChallenge() {
        return new MfaChallenge("challenge-1", "user-1", "USER", null, "alice", null, null,
                Set.of(MfaMethod.WEBAUTHN), Instant.now().plusSeconds(300));
    }

    private String assertionBody() {
        return "{\"challengeId\":\"challenge-1\",\"username\":\"alice\","
                + "\"webAuthnChallengeId\":\"wa-1\",\"credentialId\":\"cred-1\","
                + "\"clientDataJson\":\"data\",\"authenticatorData\":\"auth-data\","
                + "\"signature\":\"sig\"}";
    }
}