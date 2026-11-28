package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.session.SessionValidateResponse;
import cn.tolim.pudans.iam.contract.IamApiExceptionHandler;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.AuthenticationService;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.core.auth.session.SessionManagementService;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.security.IamSessionCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IamSessionEndpointTests {

    @Mock
    private AuthenticationService authenticationService;

    @Mock
    private SessionManagementService sessionManagementService;

    @Mock
    private ApplicationAccessService applicationAccessService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamSessionCookieService cookieService = new IamSessionCookieService(new IamAuthProperties());
        IamSessionEndpoint endpoint = new IamSessionEndpoint(authenticationService,
                sessionManagementService, applicationAccessService, cookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new IamApiExceptionHandler())
                .build();
    }

    @Test
    void meWithoutSessionIsUnauthorized() throws Exception {
        mockMvc.perform(get(IamAuthApiPaths.ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("session_not_active"));
    }

    @Test
    void validateReturnsInactiveForExpiredSession() throws Exception {
        when(sessionManagementService.validate(any(), anyString()))
                .thenReturn(SessionValidateResponse.builder().active(Boolean.FALSE)
                        .reason("session_invalid_or_expired").build());

        mockMvc.perform(post(IamAuthApiPaths.SESSION_VALIDATE_CONTRACT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sid\":\"gone\",\"appCode\":\"order\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.reason").value("session_invalid_or_expired"));
    }
}