package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.OtpSendResponse;
import cn.tolim.pudans.iam.contract.IamApiExceptionHandler;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.otp.OtpLoginService;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IamOtpLoginEndpointTests {

    @Mock
    private OtpLoginService otpLoginService;

    @Mock
    private AuthenticationService authenticationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamSessionCookieService cookieService =
                new IamSessionCookieService(new IamAuthProperties());
        IamOtpLoginEndpoint endpoint = new IamOtpLoginEndpoint(otpLoginService,
                authenticationService, cookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new IamApiExceptionHandler())
                .build();
    }

    @Test
    void sendsLoginCode() throws Exception {
        when(otpLoginService.sendCode(any())).thenReturn(OtpSendResponse.builder()
                .success(Boolean.TRUE).build());

        mockMvc.perform(post(IamAuthApiPaths.OTP_SEND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"alice@example.com\",\"channel\":\"mail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void loginReturnsSessionCookie() throws Exception {
        AuthenticatedAccount account = new AuthenticatedAccount("user-1", "alice", "user",
                "org-1", "Alice");
        LoginResponse response = new LoginResponse();
        response.setSid("sid-1");
        response.setExpiresAt(LocalDateTime.now().plusHours(8));
        when(otpLoginService.authenticate(any())).thenReturn(account);
        when(authenticationService.completeLogin(eq(account), eq("console"), eq("/home"),
                any(), any())).thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.OTP_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"alice@example.com\",\"channel\":\"mail\","
                                + "\"code\":\"123456\",\"appCode\":\"console\","
                                + "\"targetUrl\":\"/home\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("PUDANS_SID=sid-1")))
                .andExpect(jsonPath("$.sid").value("sid-1"));
    }

    @Test
    void loginWithMfaDoesNotSetCookie() throws Exception {
        AuthenticatedAccount account = new AuthenticatedAccount("user-1", "alice", "user",
                "org-1", "Alice");
        LoginResponse response = new LoginResponse();
        response.setMfaRequired(Boolean.TRUE);
        response.setMfaChallengeId("challenge-1");
        when(otpLoginService.authenticate(any())).thenReturn(account);
        when(authenticationService.completeLogin(any(), any(), any(), any(), any()))
                .thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.OTP_LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipient\":\"alice@example.com\",\"channel\":\"mail\","
                                + "\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.mfaRequired").value(true));
    }
}