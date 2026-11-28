package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.LoginConfigResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LogoutResponse;
import cn.tolim.pudans.iam.contract.IamApiExceptionHandler;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.exception.AuthenticationFailedException;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import jakarta.servlet.http.Cookie;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IamLoginEndpointTests {

    @Mock
    private AuthenticationService authenticationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamSessionCookieService cookieService = new IamSessionCookieService(new IamAuthProperties());
        IamLoginEndpoint endpoint = new IamLoginEndpoint(authenticationService, cookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new IamApiExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsSessionCookie() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setSid("sid-1");
        response.setExpiresAt(LocalDateTime.now().plusHours(8));
        response.setAppCode("order");
        when(authenticationService.login(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("PUDANS_SID=sid-1")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(jsonPath("$.sid").value("sid-1"));
    }

    @Test
    void loginWithMfaReturnsChallengeWithoutSessionCookie() throws Exception {
        LoginResponse response = new LoginResponse();
        response.setMfaRequired(Boolean.TRUE);
        response.setMfaChallengeId("challenge-1");
        when(authenticationService.login(any(), any(), any())).thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaChallengeId").value("challenge-1"))
                .andExpect(jsonPath("$.sid").doesNotExist());
    }

    @Test
    void loginRejectsBadCredentials() throws Exception {
        when(authenticationService.login(any(), any(), any()))
                .thenThrow(new AuthenticationFailedException());

        mockMvc.perform(post(IamAuthApiPaths.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("authentication_failed"));
    }

    @Test
    void loginRejectsMalformedBody() throws Exception {
        mockMvc.perform(post(IamAuthApiPaths.LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("request_body_invalid"));
    }

    @Test
    void loginConfigIsPublic() throws Exception {
        when(authenticationService.loginConfig("order"))
                .thenReturn(LoginConfigResponse.builder().appCode("order")
                        .passwordLoginEnabled(Boolean.TRUE).build());

        mockMvc.perform(get(IamAuthApiPaths.LOGIN_CONFIG_CONTRACT).param("appCode", "order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordLoginEnabled").value(true));
    }

    @Test
    void logoutClearsCookieAndRevokesCookieSession() throws Exception {
        LogoutResponse response = LogoutResponse.builder().success(Boolean.TRUE)
                .revokedSessionCount(1).notifiedApplicationCount(0)
                .revokedAt(LocalDateTime.now()).build();
        when(authenticationService.logout(any(), eq("sid-1"), any(), any())).thenReturn(response);

        mockMvc.perform(post(IamAuthApiPaths.LOGOUT_CONTRACT)
                        .cookie(new Cookie("PUDANS_SID", "sid-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("PUDANS_SID=")))
                .andExpect(jsonPath("$.success").value(true));
    }
}