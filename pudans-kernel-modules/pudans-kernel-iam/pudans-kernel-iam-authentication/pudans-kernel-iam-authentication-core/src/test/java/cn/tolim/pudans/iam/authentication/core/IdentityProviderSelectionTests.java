package cn.tolim.pudans.iam.authentication.core;

import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderBindAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderNotBindAuthentication;
import cn.tolim.pudans.iam.authentication.core.authentication.IdentityProviderUserDetails;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityProviderSelectionTests {

    @Test
    void shouldResolveKnownProviderTypes() {
        assertSame(IdentityProviderType.FEISHU_OAUTH,
                IdentityProviderType.getIdentityProviderType("feishu_oauth"));
        assertSame(IdentityProviderType.DINGTALK_OAUTH,
                IdentityProviderType.getIdentityProviderType("dingtalk_oauth"));
        assertSame(IdentityProviderType.WECHAT,
                IdentityProviderType.getIdentityProviderType("wechat_oauth"));
        assertSame(IdentityProviderType.WECHAT_WORK_OAUTH,
                IdentityProviderType.getIdentityProviderType("wechatwork_oauth"));
        assertSame(IdentityProviderType.QQ_OAUTH,
                IdentityProviderType.getIdentityProviderType("qq_oauth"));
        assertSame(IdentityProviderType.GITHUB_OAUTH,
                IdentityProviderType.getIdentityProviderType("github_oauth"));
        assertSame(IdentityProviderType.GITEE_OAUTH,
                IdentityProviderType.getIdentityProviderType("gitee_oauth"));
        assertSame(IdentityProviderType.ALIPAY_OAUTH,
                IdentityProviderType.getIdentityProviderType("alipay_oauth"));
        assertSame(IdentityProviderType.USERNAME_PASSWORD,
                IdentityProviderType.getIdentityProviderType("username_password"));
        assertSame(IdentityProviderType.SMS, IdentityProviderType.getIdentityProviderType("sms"));
        assertSame(IdentityProviderType.MAIL, IdentityProviderType.getIdentityProviderType("mail"));
    }

    @Test
    void shouldRejectUnknownProviderType() {
        assertThrows(IllegalArgumentException.class,
                () -> IdentityProviderType.getIdentityProviderType("unknown"));
        assertThrows(IllegalArgumentException.class,
                () -> IdentityProviderType.getIdentityProviderType(null));
    }

    @Test
    void shouldBuildProviderPathsAndIdentity() {
        IdentityProviderType type = IdentityProviderType.FEISHU_OAUTH;

        assertEquals(9, IdentityProviderType.size());
        assertEquals("feishu_oauth", type.value());
        assertEquals("飞书认证", type.name());
        assertTrue(type.getLoginPathPrefix().endsWith("/feishu_oauth"));
        assertTrue(type.getAuthorizationPathPrefix().endsWith("/feishu_oauth"));
        assertEquals(type, IdentityProviderType.getIdentityProviderType("feishu_oauth"));
        assertEquals(type.hashCode(), type.hashCode());
        assertNotEquals(type, IdentityProviderType.DINGTALK_OAUTH);
        assertNotEquals(type, null);
        assertTrue(type.toString().contains("feishu_oauth"));
    }

    @Test
    void shouldGroupProvidersByCategory() {
        assertSame(IdentityProviderCategory.social, IdentityProviderCategory.getType("social"));
        assertSame(IdentityProviderCategory.enterprise,
                IdentityProviderCategory.getType("enterprise"));
        assertTrue(IdentityProviderCategory.social.getProviders()
                .contains(IdentityProviderType.WECHAT));
        assertTrue(IdentityProviderCategory.enterprise.getProviders()
                .contains(IdentityProviderType.DINGTALK_OAUTH));
        assertEquals("social", IdentityProviderCategory.social.getCode());
        assertEquals("社交", IdentityProviderCategory.social.getDesc());
        assertThrows(NullPointerException.class, () -> IdentityProviderCategory.getType("other"));
    }

    @Test
    void shouldConvertIdentityProviderCategory() {
        IdentityProviderCategoryConverter converter = new IdentityProviderCategoryConverter();

        assertEquals("enterprise",
                converter.convertToDatabaseColumn(IdentityProviderCategory.enterprise));
        assertEquals(null, converter.convertToDatabaseColumn(null));
        assertSame(IdentityProviderCategory.social,
                converter.convertToEntityAttribute("social"));
        assertThrows(NullPointerException.class,
                () -> converter.convertToEntityAttribute("other"));
    }

    @Test
    void shouldBuildIdentityProviderUserDetails() {
        IdentityProviderUserDetails details = IdentityProviderUserDetails.builder()
                .openId("open-1")
                .unionId("union-1")
                .providerId("provider-1")
                .providerCode("feishu")
                .providerType(IdentityProviderType.FEISHU_OAUTH)
                .email("alice@example.com")
                .mobile("13800000000")
                .stateCode("86")
                .nickName("Alice")
                .avatarUrl("https://avatar.example.com/alice.png")
                .additionalInfo(Map.of("tenant", "t1"))
                .build();

        assertEquals("open-1", details.getOpenId());
        assertEquals("union-1", details.getUnionId());
        assertEquals("provider-1", details.getProviderId());
        assertEquals("feishu", details.getProviderCode());
        assertSame(IdentityProviderType.FEISHU_OAUTH, details.getProviderType());
        assertEquals("alice@example.com", details.getEmail());
        assertEquals(Map.of("tenant", "t1"), details.getAdditionalInfo());
    }

    @Test
    void shouldRequireProviderIdentityWhenBuildingUserDetails() {
        assertThrows(NullPointerException.class,
                () -> IdentityProviderUserDetails.builder()
                        .providerType(IdentityProviderType.FEISHU_OAUTH)
                        .build());
        assertThrows(NullPointerException.class,
                () -> IdentityProviderUserDetails.builder().providerId("provider-1").build());
        assertThrows(NullPointerException.class,
                () -> IdentityProviderUserDetails.builder()
                        .providerId("provider-1")
                        .providerType(IdentityProviderType.FEISHU_OAUTH)
                        .providerCode(null));
    }

    @Test
    void shouldExposeIdentityProviderAuthenticationTokens() {
        IdentityProviderUserDetails details = IdentityProviderUserDetails.builder()
                .openId("open-1")
                .providerId("provider-1")
                .providerType(IdentityProviderType.DINGTALK_OAUTH)
                .build();
        IdentityProviderNotBindAuthentication notBound =
                new IdentityProviderNotBindAuthentication(details);
        IdentityProviderAuthentication bound = new IdentityProviderAuthentication(details,
                IdentityProviderType.DINGTALK_OAUTH, "provider-1", List.of());
        IdentityProviderBindAuthentication bind =
                new IdentityProviderBindAuthentication("alice", "password");

        assertSame(details, notBound.getPrincipal());
        assertFalse(notBound.isAuthenticated());
        assertSame(details, bound.getPrincipal());
        assertTrue(bound.isAuthenticated());
        assertEquals("provider-1", bound.getProviderId());
        assertEquals("alice", bind.getUsername());
        assertEquals("password", bind.getPassword());
        assertEquals("alice", bind.getPrincipal());
        assertNotNull(bind.getAuthorities());
    }
}