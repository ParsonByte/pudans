package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnCredentialDescriptor;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnAuthenticationOptions;
import cn.tolim.pudans.iam.authentication.webauthn.dto.WebAuthnCredentialResponse;
import cn.tolim.pudans.iam.authentication.webauthn.service.WebAuthnService;
import cn.tolim.pudans.iam.authentication.webauthn.service.WebAuthnService.PasskeyAuthentication;
import cn.tolim.pudans.iam.contract.IamApiExceptionHandler;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService;
import cn.tolim.pudans.iam.core.auth.user.UserAccountService.AuthenticatedAccount;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IamPasskeyLoginEndpointTests {

    private static final AuthenticatedAccount ACCOUNT = new AuthenticatedAccount("user-1",
            "alice", "user", "org-1", "Alice");

    @Mock
    private WebAuthnService webAuthnService;

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private AuthenticationService authenticationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamSessionCookieService cookieService =
                new IamSessionCookieService(new IamAuthProperties());
        IamPasskeyLoginEndpoint endpoint = new IamPasskeyLoginEndpoint(webAuthnService,
                userAccountService, authenticationService, cookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new IamApiExceptionHandler())
                .build();
    }

    @Test
    void optionsResolveKnownAccount() throws Exception {
        when(userAccountService.findAccountByIdentifier("alice")).thenReturn(Optional.of(ACCOUNT));
        when(webAuthnService.authenticationOptions("user-1", "alice"))
                .thenReturn(new WebAuthnAuthenticationOptions("challenge-1", "value",
                        "example.com", 60000, "preferred",
                        List.of(new WebAuthnCredentialDescriptor("credential-1",
                                "public-key", List.of("internal")))));

        mockMvc.perform(post(IamAuthApiPaths.PASSKEY_OPTIONS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value("challenge-1"));
    }

    @Test
    void verifyCreatesSessionCookie() throws Exception {
        WebAuthnCredentialResponse credential = mock(WebAuthnCredentialResponse.class);
        when(webAuthnService.authenticate(any()))
                .thenReturn(new PasskeyAuthentication("user-1", credential));
        when(userAccountService.requireAccountById("user-1")).thenReturn(ACCOUNT);
        LoginResponse response = new LoginResponse();
        response.setSid("sid-1");
        response.setExpiresAt(LocalDateTime.now().plusHours(8));
        when(authenticationService.completeLogin(eq(ACCOUNT), eq(null), eq(null), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.PASSKEY_VERIFY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":\"challenge-1\",\"credentialId\":\"credential-1\","
                                + "\"clientDataJson\":\"client\",\"authenticatorData\":\"auth\","
                                + "\"signature\":\"signature\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("PUDANS_SID=sid-1")));
    }
}