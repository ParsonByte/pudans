package cn.tolim.pudans.iam.appkernel.core;

import cn.tolim.pudans.iam.communal.entity.app.AppAccountEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppGroupAssociationEntity;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.exception.app.AppAccountNotExistException;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractApplicationServiceTests {

    private AppAccountRepository appAccountRepository;
    private AppGroupAssociationRepository appGroupAssociationRepository;
    private AppRepository appRepository;
    private TestApplicationService service;

    @BeforeEach
    void setUp() {
        appAccountRepository = mock(AppAccountRepository.class);
        appGroupAssociationRepository = mock(AppGroupAssociationRepository.class);
        appRepository = mock(AppRepository.class);
        service = new TestApplicationService(appAccountRepository,
                appGroupAssociationRepository, appRepository);
    }

    @Test
    void shouldReturnDefaultApplicationAccount() {
        AppAccountEntity entity = new AppAccountEntity();
        entity.setAppId("app-1");
        entity.setUserId("user-1");
        entity.setAccount("alice");
        entity.setPassword("secret");
        entity.setDefaulted(true);
        when(appAccountRepository.findByAppIdAndUserIdAndDefaultedIsTrue("app-1", "user-1"))
                .thenReturn(Optional.of(entity));

        AppAccount account = service.getDefaultAppAccount("app-1", "user-1");

        assertEquals("app-1", account.getAppId());
        assertEquals("alice", account.getAccount());
        assertEquals("secret", account.getPassword());
    }

    @Test
    void shouldRejectMissingDefaultApplicationAccount() {
        when(appAccountRepository.findByAppIdAndUserIdAndDefaultedIsTrue(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(AppAccountNotExistException.class,
                () -> service.getDefaultAppAccount("app-1", "user-1"));
    }

    @Test
    void shouldCreateApplicationWithProtocolDefaults() {
        doAnswer(invocation -> invocation.getArgument(0))
                .when(appRepository).save(any(AppEntity.class));

        AppEntity app = service.createApp("Order", "icon", "remark",
                AuthorizationType.AUTHORIZATION);

        assertEquals("Order", app.getName());
        assertEquals("icon", app.getIcon());
        assertEquals("remark", app.getRemark());
        assertEquals("test", app.getTemplate());
        assertEquals(AppType.STANDARD, app.getType());
        assertEquals(AppProtocol.JWT, app.getProtocol());
        assertEquals(AuthorizationType.AUTHORIZATION, app.getAuthorizationType());
        assertEquals(true, app.getEnabled());
        assertEquals(false, app.getConfigured());
        assertEquals(32, app.getCode().length());
        assertEquals(app.getCode().toLowerCase(), app.getCode());
        assertEquals(32, app.getClientId().length());
        assertEquals(32, app.getClientSecret().length());
        assertFalse(app.getClientId().contains("-"));
        assertFalse(app.getClientSecret().contains("-"));
        verify(appRepository).save(app);
    }

    @Test
    void shouldCreateGroupAssociationsForNewApplication() {
        doAnswer(invocation -> invocation.getArgument(0))
                .when(appRepository).save(any(AppEntity.class));

        AppEntity app = service.createApp("Order", "icon", "remark",
                List.of("group-1", "group-2"), AuthorizationType.ALL_ACCESS);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AppGroupAssociationEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(appGroupAssociationRepository).saveAll(captor.capture());
        List<AppGroupAssociationEntity> associations = captor.getValue();
        assertEquals(2, associations.size());
        assertEquals("group-1", associations.getFirst().getGroupId());
        assertEquals("group-2", associations.getLast().getGroupId());
        assertSame(app, associations.getFirst().getApp());
        assertEquals(AuthorizationType.ALL_ACCESS, app.getAuthorizationType());
    }

    // Application service exposing the shared base behavior under test
    private static final class TestApplicationService extends AbstractApplicationService {

        private TestApplicationService(AppAccountRepository appAccountRepository,
                                       AppGroupAssociationRepository appGroupAssociationRepository,
                                       AppRepository appRepository) {
            super(appAccountRepository, appGroupAssociationRepository, appRepository);
        }

        @Override
        public String getCode() {
            return "test";
        }

        @Override
        public String getName() {
            return "Test";
        }

        @Override
        public String getDescription() {
            return "Test application";
        }

        @Override
        public AppType getType() {
            return AppType.STANDARD;
        }

        @Override
        public AppProtocol getProtocol() {
            return AppProtocol.JWT;
        }

        @Override
        public String getBase64Icon() {
            return "data:image/svg+xml;base64,";
        }

        @Override
        public String create(String name, String icon, String remark, List<String> groups) {
            return createApp(name, icon, remark, groups, AuthorizationType.AUTHORIZATION).getId();
        }

        @Override
        public void delete(String appId) {
        }

        @Override
        public void saveConfig(String appId, Map<String, Object> config) {
        }

        @Override
        public Object getConfig(String appId) {
            return null;
        }
    }
}