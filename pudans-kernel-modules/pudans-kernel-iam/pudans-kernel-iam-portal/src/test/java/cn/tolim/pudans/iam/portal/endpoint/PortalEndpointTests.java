package cn.tolim.pudans.iam.portal.endpoint;

import cn.tolim.pudans.iam.portal.dto.PortalAppsResponse;
import cn.tolim.pudans.iam.portal.dto.PortalLoginConfigResponse;
import cn.tolim.pudans.iam.portal.dto.PortalLogoutUrlResponse;
import cn.tolim.pudans.iam.portal.dto.PortalMeResponse;
import cn.tolim.pudans.iam.portal.dto.PortalUserView;
import cn.tolim.pudans.iam.portal.service.PortalAppService;
import cn.tolim.pudans.iam.portal.service.PortalLoginConfigService;
import cn.tolim.pudans.iam.portal.service.PortalMeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PortalEndpointTests {

    private final PortalMeService portalMeService = mock(PortalMeService.class);
    private final PortalAppService portalAppService = mock(PortalAppService.class);
    private final PortalLoginConfigService portalLoginConfigService =
            mock(PortalLoginConfigService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new PortalEndpoint(portalMeService, portalAppService, portalLoginConfigService))
                .build();
    }

    @Test
    void returnsCurrentUser() throws Exception {
        PortalMeResponse response = new PortalMeResponse();
        response.setAuthenticated(Boolean.TRUE);
        PortalUserView user = new PortalUserView();
        user.setUserId("user-1");
        user.setUsername("alice");
        response.setUser(user);
        when(portalMeService.getCurrentUser(any())).thenReturn(response);

        mockMvc.perform(get("/apis/pudans.tolim.cn/v1alpha1/iam/portal/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andExpect(jsonPath("$.result.user.username").value("alice"));
    }

    @Test
    void servesCanonicalIamPath() throws Exception {
        PortalMeResponse response = new PortalMeResponse();
        response.setAuthenticated(Boolean.FALSE);
        when(portalMeService.getCurrentUser(any())).thenReturn(response);

        mockMvc.perform(get("/apis/pudans.tolim.cn/v1alpha1/iam/portal/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.authenticated").value(false));
    }

    @Test
    void returnsAuthorizedApps() throws Exception {
        PortalAppsResponse response = new PortalAppsResponse();
        response.setAuthenticated(Boolean.FALSE);
        when(portalAppService.getAuthorizedApps(any())).thenReturn(response);

        mockMvc.perform(get("/apis/pudans.tolim.cn/v1alpha1/iam/portal/apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.authenticated").value(false))
                .andExpect(jsonPath("$.result.apps").isArray());
    }

    @Test
    void returnsLoginConfig() throws Exception {
        PortalLoginConfigResponse response = new PortalLoginConfigResponse();
        response.setHomeUrl("https://home.test");
        response.setLoginUrl("https://home.test/login");
        when(portalLoginConfigService.getLoginConfig(any())).thenReturn(response);

        mockMvc.perform(get("/apis/pudans.tolim.cn/v1alpha1/iam/portal/login-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.homeUrl").value("https://home.test"))
                .andExpect(jsonPath("$.result.loginUrl").value("https://home.test/login"));
    }

    @Test
    void returnsLogoutUrl() throws Exception {
        PortalLogoutUrlResponse response = new PortalLogoutUrlResponse();
        response.setLogoutUrl("https://home.test/logout");
        when(portalLoginConfigService.getLogoutUrl(any())).thenReturn(response);

        mockMvc.perform(get("/apis/pudans.tolim.cn/v1alpha1/iam/portal/logout-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.logoutUrl").value("https://home.test/logout"))
                .andExpect(jsonPath("$.result.method").value("GLOBAL"));
    }
}