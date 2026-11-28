package cn.tolim.pudans.iam.appkernel.jwt.converter;

import cn.tolim.pudans.iam.appkernel.jwt.pojo.AppJwtConfigGetResult;
import cn.tolim.pudans.iam.appkernel.jwt.pojo.AppJwtSaveConfigParam;
import cn.tolim.pudans.iam.communal.entity.app.AppJwtConfigEntity;
import cn.tolim.pudans.iam.communal.entity.app.po.AppJwtConfigPO;
import cn.tolim.pudans.iam.communal.enums.app.AuthorizationType;
import cn.tolim.pudans.iam.communal.enums.app.JwtBindingType;
import cn.tolim.pudans.iam.communal.enums.app.JwtIdTokenSubjectType;
import cn.tolim.pudans.iam.core.context.ContextService;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppJwtConfigConverterTests {

    private static final String BASE_URL = "https://iam.example.com";

    private final AppJwtConfigConverter converter = new AppJwtConfigConverter() {
    };

    @Test
    void shouldConvertSaveParamToEntity() {
        AppJwtSaveConfigParam param = new AppJwtSaveConfigParam();
        param.setRedirectUrl("https://app.example.com/sso");
        param.setTargetLinkUrl("https://app.example.com/home");
        param.setBindingType(JwtBindingType.POST);
        param.setIdTokenSubjectType(JwtIdTokenSubjectType.APP_USER);
        param.setIdTokenTimeToLive(600);

        AppJwtConfigEntity entity = converter.appJwtSaveConfigParamToEntity(param);

        assertNotNull(entity);
        assertEquals("https://app.example.com/sso", entity.getRedirectUrl());
        assertEquals("https://app.example.com/home", entity.getTargetLinkUrl());
        assertEquals(JwtBindingType.POST, entity.getBindingType());
        assertEquals(JwtIdTokenSubjectType.APP_USER, entity.getIdTokenSubjectType());
        assertEquals(Duration.ofSeconds(600), entity.getIdTokenTimeToLive());
    }

    @Test
    void shouldReturnNullForNullSaveParam() {
        assertNull(converter.appJwtSaveConfigParamToEntity(null));
        assertNull(converter.entityConverterToFormConfigResult(null));
    }

    @Test
    void shouldConvertProjectionToConfigResult() {
        AppJwtConfigEntity config = new AppJwtConfigEntity();
        config.setAppId("app-1");
        config.setRedirectUrl("https://app.example.com/sso");
        config.setTargetLinkUrl("https://app.example.com/home");
        config.setBindingType(JwtBindingType.REDIRECT);
        config.setIdTokenSubjectType(JwtIdTokenSubjectType.USER_ID);
        config.setIdTokenTimeToLive(Duration.ofSeconds(900));
        AppJwtConfigPO po = new AppJwtConfigPO(config, "app-code", "Order", "jwt",
                "client-1", "secret-1", "https://iam.example.com/init",
                AuthorizationType.AUTHORIZATION, true, true);

        try (MockedStatic<ContextService> context = Mockito.mockStatic(ContextService.class)) {
            context.when(ContextService::getPortalPublicBaseUrl).thenReturn(BASE_URL);

            AppJwtConfigGetResult result = converter.entityConverterToFormConfigResult(po);

            assertEquals("app-1", result.getAppId());
            assertEquals("https://iam.example.com/init", result.getInitLoginUrl());
            assertEquals("https://app.example.com/sso", result.getRedirectUrl());
            assertEquals("https://app.example.com/home", result.getTargetLinkUrl());
            assertEquals(JwtBindingType.REDIRECT, result.getBindingType());
            assertEquals(JwtIdTokenSubjectType.USER_ID, result.getIdTokenSubjectType());
            assertEquals("900", result.getIdTokenTimeToLive());
            assertNotNull(result.getProtocolEndpoint());
            assertTrue(result.getProtocolEndpoint().getIdpSsoEndpoint()
                    .startsWith(BASE_URL));
            assertTrue(result.getProtocolEndpoint().getIdpSsoEndpoint()
                    .contains("/jwt/app-code/sso"));
            assertTrue(!result.getProtocolEndpoint().getIdpSsoEndpoint()
                    .contains("{appCode}"));
        }
    }
}