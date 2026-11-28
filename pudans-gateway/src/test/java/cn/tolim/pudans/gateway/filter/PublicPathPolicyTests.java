package cn.tolim.pudans.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

class PublicPathPolicyTests {

    @Test
    void loginPostIsPublicApi() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.LOGIN_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.LOGIN_PATH)).isFalse();
    }

    @Test
    void loginPageAndHomepageArePublicPages() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.HOME_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.LOGIN_PAGE_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.LOGIN_PAGE_PREFIX + "mfa")).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/loginx")).isFalse();
    }

    @Test
    void staticAssetsArePublic() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/assets/app.js")).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/static/logo.svg")).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/favicon.ico")).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/assetsx/app.js")).isFalse();
    }

    @Test
    void sessionAndTicketEndpointsArePublicForPostOnly() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.SESSION_VALIDATE_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.SESSIONS_VALIDATE_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.TICKET_ISSUE_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.TICKET_EXCHANGE_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.SESSION_VALIDATE_PATH)).isFalse();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.TICKET_EXCHANGE_PATH)).isFalse();
    }

    @Test
    void loginFlowEndpointsArePublic() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.LOGOUT_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.MFA_CHALLENGE_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.MFA_VERIFY_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.FORGOT_PASSWORD_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.RESET_PASSWORD_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.CSRF_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.CAPTCHA_PATH)).isTrue();
    }

    @Test
    void ssoCallbacksArePublicButOtherAppPagesAreNot() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/apps/order/sso/callback")).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, "/apps/order/sso/backchannel-logout")).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/apps/order/orders/42")).isFalse();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/apps/order/sso/callback/extra")).isFalse();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, "/apps/order/callback")).isFalse();
    }

    @Test
    void actuatorHealthAndInfoArePublicButOtherActuatorPathsAreNot() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.HEALTH_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.INFO_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.ACTUATOR_PATH + "/env")).isFalse();
    }

    @Test
    void protectedIamPathsAreNotPublic() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET,
                "/apis/pudans.tolim.cn/v1alpha1/iam/users")).isFalse();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.DELETE,
                "/apis/pudans.tolim.cn/v1alpha1/iam/users/u-1")).isFalse();
    }

    @Test
    void loginConfigAndPortalPathsArePublicForGetOnly() {
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.LOGIN_CONFIG_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.PORTAL_LOGIN_CONFIG_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET, PublicPathPolicy.PORTAL_LOGOUT_URL_PATH)).isTrue();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.POST, PublicPathPolicy.LOGIN_CONFIG_PATH)).isFalse();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET,
                PublicPathPolicy.PORTAL_PATH_PREFIX + "me")).isFalse();
        assertThat(PublicPathPolicy.isPublic(HttpMethod.GET,
                PublicPathPolicy.PORTAL_PATH_PREFIX + "apps")).isFalse();
    }

    @Test
    void apiPathsAreRoutedAndResolveServiceAppCodes() {
        assertThat(PublicPathPolicy.isRoutedPath(PublicPathPolicy.PORTAL_LOGIN_CONFIG_PATH)).isTrue();
        assertThat(PublicPathPolicy.isRoutedPath(PublicPathPolicy.API_BASE_PATH + "/mq/messages")).isTrue();
        assertThat(PublicPathPolicy.isRoutedPath("/api")).isFalse();
        assertThat(PublicPathPolicy.isRoutedPath("/apiary/mq")).isFalse();

        assertThat(PublicPathPolicy.resolveAppCode(PublicPathPolicy.API_BASE_PATH + "/mq/messages"))
                .isEqualTo("mq");
        assertThat(PublicPathPolicy.resolveAppCode(PublicPathPolicy.PORTAL_PATH_PREFIX + "me"))
                .isEqualTo(PublicPathPolicy.IAM_APP_CODE);
        assertThat(PublicPathPolicy.resolveAppCode(PublicPathPolicy.API_BASE_PATH + "/notification/send"))
                .isEqualTo("notification");
        assertThat(PublicPathPolicy.resolveAppCode(
                PublicPathPolicy.API_BASE_PATH + "/repository/files")).isEqualTo("repository");
        assertThat(PublicPathPolicy.resolveAppCode(
                PublicPathPolicy.API_BASE_PATH + "/job/jobs")).isEqualTo("job");
        assertThat(PublicPathPolicy.resolveAppCode(
                PublicPathPolicy.API_BASE_PATH + "/search/query")).isEqualTo("search");
    }

    @Test
    void routedPathsExcludeUnknownPaths() {
        assertThat(PublicPathPolicy.isRoutedPath("/apis/pudans.tolim.cn/v1alpha1/iam/users")).isTrue();
        assertThat(PublicPathPolicy.isRoutedPath(PublicPathPolicy.HOME_PATH)).isTrue();
        assertThat(PublicPathPolicy.isRoutedPath("/login")).isTrue();
        assertThat(PublicPathPolicy.isRoutedPath("/apps/order/orders/1")).isTrue();
        assertThat(PublicPathPolicy.isRoutedPath("/other/path")).isFalse();
        assertThat(PublicPathPolicy.isRoutedPath("/actuator/health")).isFalse();
        assertThat(PublicPathPolicy.isRoutedPath("/apis/pudans.tolim.cn/v1alpha1xxx/iam/users")).isFalse();
    }

    @Test
    void appCodeIsResolvedFromPageAndApiPaths() {
        assertThat(PublicPathPolicy.resolveAppCode("/apps/order/orders/1")).isEqualTo("order");
        assertThat(PublicPathPolicy.resolveAppCode("/apps/order")).isEqualTo("order");
        assertThat(PublicPathPolicy.resolveAppCode("/apps/order/sso/callback")).isEqualTo("order");
        assertThat(PublicPathPolicy.resolveAppCode("/apis/pudans.tolim.cn/v1alpha1/order/orders"))
                .isEqualTo("order");
        assertThat(PublicPathPolicy.resolveAppCode("/apis/pudans.tolim.cn/v1alpha1/iam/users"))
                .isEqualTo("iam");
        assertThat(PublicPathPolicy.resolveAppCode("/login")).isNull();
        assertThat(PublicPathPolicy.resolveAppCode("/other/path")).isNull();
    }
}