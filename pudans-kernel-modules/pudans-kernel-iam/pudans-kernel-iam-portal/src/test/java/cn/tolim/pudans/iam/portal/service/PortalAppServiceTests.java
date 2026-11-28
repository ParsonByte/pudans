package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppGroupEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import cn.tolim.pudans.iam.core.auth.user.ApplicationAccessService;
import cn.tolim.pudans.iam.infra.autoconfiguration.SupportProperties;
import cn.tolim.pudans.iam.portal.config.PortalProperties;
import cn.tolim.pudans.iam.portal.dto.PortalAppView;
import cn.tolim.pudans.iam.portal.dto.PortalAppsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortalAppServiceTests {

    private final PortalSessionService portalSessionService = mock(PortalSessionService.class);
    private final ApplicationAccessService applicationAccessService =
            mock(ApplicationAccessService.class);
    private final AppRepository appRepository = mock(AppRepository.class);
    private final AppGroupAssociationRepository appGroupAssociationRepository =
            mock(AppGroupAssociationRepository.class);
    private final AppGroupRepository appGroupRepository = mock(AppGroupRepository.class);
    private final PortalProperties portalProperties = new PortalProperties();
    private final PortalAppService service = new PortalAppService(portalSessionService,
            applicationAccessService, appRepository, appGroupAssociationRepository,
            appGroupRepository, new PortalUrlResolver(portalProperties, supportProvider()));

    @Test
    void listsAuthorizedAppsWithLaunchUrlsAndGroups() {
        portalProperties.setHomeBaseUrl("https://home.test/");
        when(portalSessionService.resolve(any())).thenReturn(Optional.of(identity()));
        AppEntity direct = app("app-1", "order", "Order", "https://order.test/sso",
                AuthorizationType.AUTHORIZATION);
        AppEntity throughLogin = app("app-2", "report", "Report", null,
                AuthorizationType.AUTHORIZATION);
        AppEntity allAccess = app("app-3", "wiki", "Wiki", null, AuthorizationType.ALL_ACCESS);
        AppEntity denied = app("app-4", "billing", "Billing", null, AuthorizationType.AUTHORIZATION);
        when(appRepository.findAll()).thenReturn(List.of(direct, throughLogin, allAccess, denied));
        when(applicationAccessService.canAccess("user-1", "org-1", "USER", direct)).thenReturn(true);
        when(applicationAccessService.canAccess("user-1", "org-1", "USER", throughLogin))
                .thenReturn(true);
        when(applicationAccessService.canAccess("user-1", "org-1", "USER", allAccess))
                .thenReturn(false);
        when(appGroupAssociationRepository.findGroupIdByAppId("app-2"))
                .thenReturn(List.of("group-1"));
        AppGroupEntity group = new AppGroupEntity();
        group.setId("group-1");
        group.setCode("ops");
        group.setName("Operations");
        when(appGroupRepository.findAllById(List.of("group-1"))).thenReturn(List.of(group));

        PortalAppsResponse response = service.getAuthorizedApps(new MockHttpServletRequest());

        assertThat(response.getAuthenticated()).isTrue();
        assertThat(response.getApps()).hasSize(3);
        Map<String, PortalAppView> byCode = response.getApps().stream()
                .collect(Collectors.toMap(PortalAppView::getAppCode, Function.identity()));
        assertThat(byCode).doesNotContainKey("billing");
        assertThat(byCode.get("order").getLaunchUrl()).isEqualTo("https://order.test/sso");
        assertThat(byCode.get("report").getLaunchUrl())
                .isEqualTo("https://home.test/login?app=report");
        assertThat(byCode.get("wiki").getLaunchUrl())
                .isEqualTo("https://home.test/login?app=wiki");
        assertThat(byCode.get("report").getGroups()).hasSize(1);
        assertThat(byCode.get("report").getGroups().get(0).getCode()).isEqualTo("ops");
        assertThat(byCode.get("report").getProtocol()).isEqualTo(AppProtocol.JWT.getCode());
        assertThat(byCode.get("report").getType()).isEqualTo(AppType.STANDARD.getCode());
    }

    @Test
    void returnsEmptyForAnonymousCaller() {
        when(portalSessionService.resolve(any())).thenReturn(Optional.empty());

        PortalAppsResponse response = service.getAuthorizedApps(new MockHttpServletRequest());

        assertThat(response.getAuthenticated()).isFalse();
        assertThat(response.getApps()).isEmpty();
        verifyNoInteractions(appRepository);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SupportProperties> supportProvider() {
        ObjectProvider<SupportProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }

    private static PortalIdentity identity() {
        return new PortalIdentity("user-1", "alice", "Alice", "USER", "org-1", "Org", null, null,
                null, null, null, List.of(), null);
    }

    private static AppEntity app(String id, String code, String name, String initLoginUrl,
                                 AuthorizationType authorizationType) {
        AppEntity app = new AppEntity();
        app.setId(id);
        app.setCode(code);
        app.setName(name);
        app.setInitLoginUrl(initLoginUrl);
        app.setProtocol(AppProtocol.JWT);
        app.setType(AppType.STANDARD);
        app.setTemplate("template");
        app.setAuthorizationType(authorizationType);
        app.setEnabled(Boolean.TRUE);
        return app;
    }
}