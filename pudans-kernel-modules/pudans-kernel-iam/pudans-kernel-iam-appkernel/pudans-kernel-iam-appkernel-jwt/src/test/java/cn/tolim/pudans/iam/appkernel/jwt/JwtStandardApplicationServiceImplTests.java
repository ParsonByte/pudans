package cn.tolim.pudans.iam.appkernel.jwt;

import cn.tolim.pudans.iam.appkernel.core.exception.AppNotExistException;
import cn.tolim.pudans.iam.appkernel.jwt.converter.AppJwtConfigConverter;
import cn.tolim.pudans.iam.appkernel.jwt.pojo.AppJwtConfigGetResult;
import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppJwtConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO;
import cn.tolim.pudans.iam.communal.enums.app.AppProtocol;
import cn.tolim.pudans.iam.communal.enums.app.AppType;
import cn.tolim.pudans.iam.communal.enums.app.JwtBindingType;
import cn.tolim.pudans.iam.communal.enums.app.JwtIdTokenSubjectType;
import cn.tolim.pudans.iam.communal.repository.app.AppAccessPolicyRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppAccountRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppCertRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppGroupAssociationRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppJwtConfigRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class JwtStandardApplicationServiceImplTests {

    private AppJwtConfigRepository appJwtConfigRepository;
    private AppRepository appRepository;
    private JwtStandardApplicationServiceImpl service;

    @BeforeEach
    void setUp() {
        appJwtConfigRepository = mock(AppJwtConfigRepository.class);
        appRepository = mock(AppRepository.class);
        AppJwtConfigConverter converter = new AppJwtConfigConverter() {
        };
        service = new JwtStandardApplicationServiceImpl(appJwtConfigRepository,
                mock(AppGroupAssociationRepository.class), converter,
                mock(AppCertRepository.class), appRepository, mock(AppAccountRepository.class),
                mock(AppAccessPolicyRepository.class));
    }

    private static Map<String, Object> validConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("initLoginUrl", "https://iam.example.com/init");
        config.put("redirectUrl", "https://app.example.com/sso");
        config.put("targetLinkUrl", "https://app.example.com/home");
        config.put("bindingType", JwtBindingType.POST.getCode());
        config.put("idTokenSubjectType", JwtIdTokenSubjectType.USER_ID.getCode());
        config.put("idTokenTimeToLive", 600);
        return config;
    }

    @Test
    void shouldExposeJwtProtocolMetadata() {
        assertEquals("JWT", service.getCode());
        assertEquals(AppProtocol.JWT.getDesc(), service.getName());
        assertEquals(AppType.STANDARD, service.getType());
        assertEquals(AppProtocol.JWT, service.getProtocol());
        assertTrue(service.getDescription().contains("JWT"));
        assertTrue(service.getBase64Icon().startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    void shouldSaveValidConfiguration() {
        AppEntity app = new AppEntity();
        AppJwtConfigEntity entity = new AppJwtConfigEntity();
        when(appRepository.findById("app-1")).thenReturn(Optional.of(app));
        when(appJwtConfigRepository.findByAppId("app-1")).thenReturn(Optional.of(entity));
        when(appRepository.save(any(AppEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(appJwtConfigRepository.save(any(AppJwtConfigEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.saveConfig("app-1", validConfig());

        assertEquals(Boolean.TRUE, app.getConfigured());
        assertEquals("https://iam.example.com/init", app.getInitLoginUrl());
        verify(appRepository).save(app);
        verify(appJwtConfigRepository).save(entity);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        Map<String, Object> config = validConfig();
        config.remove("redirectUrl");

        assertThrows(ConstraintViolationException.class,
                () -> service.saveConfig("app-1", config));
        verifyNoInteractions(appRepository);
        verifyNoInteractions(appJwtConfigRepository);
    }

    @Test
    void shouldRejectUnknownApplication() {
        when(appRepository.findById("app-1")).thenReturn(Optional.empty());

        assertThrows(AppNotExistException.class, () -> service.saveConfig("app-1", validConfig()));
        verify(appJwtConfigRepository, never()).save(any());
    }

    @Test
    void shouldRejectMissingJwtConfiguration() {
        when(appRepository.findById("app-1")).thenReturn(Optional.of(new AppEntity()));
        when(appJwtConfigRepository.findByAppId("app-1")).thenReturn(Optional.empty());

        assertThrows(AppNotExistException.class, () -> service.saveConfig("app-1", validConfig()));
        verify(appJwtConfigRepository, never()).save(any());
    }

    @Test
    void shouldDelegateGetConfigToRepositoryAndConverter() {
        AppJwtConfigPO po = mock(AppJwtConfigPO.class);
        AppJwtConfigGetResult expected = new AppJwtConfigGetResult();
        AppJwtConfigConverter converter = mock(AppJwtConfigConverter.class);
        when(converter.entityConverterToFormConfigResult(po)).thenReturn(expected);
        when(appJwtConfigRepository.getByAppId("app-1")).thenReturn(po);
        JwtStandardApplicationServiceImpl readService = new JwtStandardApplicationServiceImpl(
                appJwtConfigRepository, mock(AppGroupAssociationRepository.class), converter,
                mock(AppCertRepository.class), appRepository, mock(AppAccountRepository.class),
                mock(AppAccessPolicyRepository.class));

        Object result = readService.getConfig("app-1");

        assertNotNull(result);
        assertSame(expected, result);
        verify(converter).entityConverterToFormConfigResult(po);
    }
}