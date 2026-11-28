package cn.tolim.pudans.iam.appkernel.oidc;

import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.oidc.converter.AppOidcStandardConfigConverter;
import cn.tolim.pudans.iam.appkernel.oidc.pojo.AppOidcStandardConfigGetResult;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppOidcConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppOidcConfigPO;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppCertRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppOidcConfigRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OidcStandardApplicationServiceImplTests {

    private AppOidcConfigRepository appOidcConfigRepository;
    private AppRepository appRepository;
    private AppOidcStandardConfigConverter converter;
    private OidcStandardApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        appOidcConfigRepository = mock(AppOidcConfigRepository.class);
        appRepository = mock(AppRepository.class);
        converter = new AppOidcStandardConfigConverter() {
        };
        service = new OidcStandardApplicationServiceImpl(mock(AppCertRepository.class),
                mock(AppGroupAssociationRepository.class), mock(AppAccountRepository.class),
                mock(AppAccessPolicyRepository.class), appRepository, appOidcConfigRepository,
                converter);
    }

    private static Map<String, Object> validConfig(boolean requireProofKey,
                                                   List<String> clientAuthMethods) {
        Map<String, Object> config = new HashMap<>();
        config.put("initLoginUrl", "https://iam.example.com/init");
        config.put("authGrantTypes", List.of("authorization_code"));
        config.put("redirectUris", List.of("https://app.example.com/callback"));
        config.put("postLogoutRedirectUris", List.of("https://app.example.com/logout"));
        config.put("grantScopes", List.of("openid"));
        config.put("clientAuthMethods", clientAuthMethods);
        config.put("requireProofKey", requireProofKey);
        config.put("accessTokenTimeToLive", 20);
        config.put("refreshTokenTimeToLive", 43200);
        config.put("authorizationCodeTimeToLive", 5);
        config.put("deviceCodeTimeToLive", 5);
        config.put("idTokenTimeToLive", 30);
        config.put("idTokenSignatureAlgorithm", "RS256");
        config.put("accessTokenFormat", "reference");
        config.put("reuseRefreshToken", false);
        return config;
    }

    private static AppOidcConfigEntity configEntity() {
        AppOidcConfigEntity entity = new AppOidcConfigEntity();
        entity.setClientAuthMethods(new HashSet<>(List.of("client_secret_basic")));
        return entity;
    }

    @Test
    void shouldExposeOidcProtocolMetadata() {
        assertEquals("oidc", service.getCode());
        assertEquals("OIDC", service.getName());
        assertEquals(AppType.STANDARD, service.getType());
        assertEquals(AppProtocol.OIDC, service.getProtocol());
        assertTrue(service.getDescription().contains("OIDC"));
        assertTrue(service.getBase64Icon().startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    void shouldSaveConfigurationAndEnablePkce() {
        AppEntity app = new AppEntity();
        AppOidcConfigEntity entity = configEntity();
        entity.setRequireProofKey(Boolean.TRUE);
        when(appRepository.findById("app-1")).thenReturn(Optional.of(app));
        when(appOidcConfigRepository.findByAppId("app-1")).thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(appOidcConfigRepository.save(any(AppOidcConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveConfig("app-1",
                validConfig(true, List.of("client_secret_basic", "none")));

        assertEquals(Boolean.TRUE, app.getConfigured());
        assertEquals("https://iam.example.com/init", app.getInitLoginUrl());
        assertTrue(entity.getClientAuthMethods().contains("none"));
        verify(appRepository).save(app);
        verify(appOidcConfigRepository).save(entity);
    }

    @Test
    void shouldRemoveNoneAuthMethodWhenPkceIsDisabled() {
        AppEntity app = new AppEntity();
        AppOidcConfigEntity entity = configEntity();
        entity.getClientAuthMethods().add("none");
        entity.setRequireProofKey(Boolean.FALSE);
        when(appRepository.findById("app-1")).thenReturn(Optional.of(app));
        when(appOidcConfigRepository.findByAppId("app-1")).thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(appOidcConfigRepository.save(any(AppOidcConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveConfig("app-1",
                validConfig(false, List.of("client_secret_basic")));

        assertFalse(entity.getClientAuthMethods().contains("none"));
        verify(appOidcConfigRepository).save(entity);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        Map<String, Object> config = validConfig(false, List.of("client_secret_basic"));
        config.remove("idTokenSignatureAlgorithm");

        assertThrows(ConstraintViolationException.class,
                () -> service.saveConfig("app-1", config));
        verifyNoInteractions(appRepository);
        verifyNoInteractions(appOidcConfigRepository);
    }

    @Test
    void shouldRejectUnknownApplication() {
        when(appRepository.findById("app-1")).thenReturn(Optional.empty());

        assertThrows(AppNotExistException.class, () -> service.saveConfig("app-1",
                validConfig(false, List.of("client_secret_basic"))));
        verify(appOidcConfigRepository, never()).save(any());
    }

    @Test
    void shouldRejectMissingOidcConfiguration() {
        when(appRepository.findById("app-1")).thenReturn(Optional.of(new AppEntity()));
        when(appOidcConfigRepository.findByAppId("app-1")).thenReturn(Optional.empty());

        assertThrows(AppNotExistException.class, () -> service.saveConfig("app-1",
                validConfig(false, List.of("client_secret_basic"))));
        verify(appOidcConfigRepository, never()).save(any());
    }

    @Test
    void shouldDelegateGetConfigToRepositoryAndConverter() {
        AppOidcConfigPO po = mock(AppOidcConfigPO.class);
        AppOidcStandardConfigGetResult expected = new AppOidcStandardConfigGetResult();
        AppOidcStandardConfigConverter mockedConverter = mock(AppOidcStandardConfigConverter.class);
        when(mockedConverter.entityConverterToOidcConfigResult(po)).thenReturn(expected);
        when(appOidcConfigRepository.getByAppId("app-1")).thenReturn(po);
        OidcStandardApplicationServiceImpl readService =
                new OidcStandardApplicationServiceImpl(mock(AppCertRepository.class),
                        mock(AppGroupAssociationRepository.class), mock(AppAccountRepository.class),
                        mock(AppAccessPolicyRepository.class), appRepository,
                        appOidcConfigRepository, mockedConverter);

        Object result = readService.getConfig("app-1");

        assertNotNull(result);
        assertSame(expected, result);
        verify(mockedConverter).entityConverterToOidcConfigResult(po);
    }
}