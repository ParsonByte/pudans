package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.ForgotPasswordResponse;
import cn.tolim.pudans.contract.iam.dto.auth.PasswordResetResponse;
import cn.tolim.pudans.iam.contract.IamApiExceptionHandler;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.password.PasswordResetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IamPasswordResetEndpointTests {

    @Mock
    private PasswordResetService passwordResetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamPasswordResetEndpoint endpoint = new IamPasswordResetEndpoint(passwordResetService);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new IamApiExceptionHandler())
                .build();
    }

    @Test
    void forgotPasswordSendsCode() throws Exception {
        when(passwordResetService.request(any())).thenReturn(ForgotPasswordResponse.builder()
                .success(Boolean.TRUE).message("sent").build());

        mockMvc.perform(post(IamAuthApiPaths.FORGOT_PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice@example.com\",\"channel\":\"mail\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void resetPasswordReturnsSuccess() throws Exception {
        when(passwordResetService.reset(any())).thenReturn(PasswordResetResponse.builder()
                .success(Boolean.TRUE).message("reset").build());

        mockMvc.perform(post(IamAuthApiPaths.RESET_PASSWORD)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"alice@example.com\",\"channel\":\"mail\","
                                + "\"token\":\"123456\",\"newPassword\":\"new-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}