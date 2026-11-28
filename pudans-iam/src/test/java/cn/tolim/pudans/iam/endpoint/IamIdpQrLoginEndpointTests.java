package cn.tolim.pudans.iam.endpoint;

import cn.tolim.pudans.contract.iam.dto.auth.IdpQrPollResponse;
import cn.tolim.pudans.contract.iam.dto.auth.IdpQrStartResponse;
import cn.tolim.pudans.contract.iam.dto.auth.LoginResponse;
import cn.tolim.pudans.iam.contract.IamApiExceptionHandler;
import cn.tolim.pudans.iam.contract.IamAuthApiPaths;
import cn.tolim.pudans.iam.core.auth.IamAuthProperties;
import cn.tolim.pudans.iam.idp.IdpQrLoginService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class IamIdpQrLoginEndpointTests {

    @Mock
    private IdpQrLoginService idpQrLoginService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        IamSessionCookieService cookieService =
                new IamSessionCookieService(new IamAuthProperties());
        IamIdpQrLoginEndpoint endpoint = new IamIdpQrLoginEndpoint(idpQrLoginService,
                cookieService);
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint)
                .setControllerAdvice(new IamApiExceptionHandler())
                .build();
    }

    @Test
    void startsQrLogin() throws Exception {
        when(idpQrLoginService.start(any(), anyString())).thenReturn(IdpQrStartResponse.builder()
                .state("state-1").url("https://provider/auth").build());

        mockMvc.perform(post(IamAuthApiPaths.IDP_QR_START)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"dingtalk_oauth\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("state-1"));
    }

    @Test
    void pollCreatesSessionCookie() throws Exception {
        LoginResponse login = new LoginResponse();
        login.setSid("sid-1");
        login.setExpiresAt(LocalDateTime.now().plusHours(1));
        when(idpQrLoginService.poll(any(), any(), any())).thenReturn(IdpQrPollResponse.builder()
                .status("success").login(login).build());

        mockMvc.perform(post(IamAuthApiPaths.IDP_QR_POLL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"state\":\"state-1\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("PUDANS_SID=sid-1")))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void scanRedirectsToProvider() throws Exception {
        when(idpQrLoginService.scan(any(), any(), any()))
                .thenReturn(IdpQrLoginService.IdpScanResult.redirect("https://provider/auth"));

        mockMvc.perform(get(IamAuthApiPaths.IDP_QR_SCAN).param("state", "state-1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://provider/auth"));
    }

    @Test
    void callbackReturnsHtmlPage() throws Exception {
        when(idpQrLoginService.callback("code-1", "state-1"))
                .thenReturn("<html>ok</html>");

        mockMvc.perform(get(IamAuthApiPaths.IDP_QR_CALLBACK)
                        .param("code", "code-1")
                        .param("state", "state-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("ok")));
    }
}