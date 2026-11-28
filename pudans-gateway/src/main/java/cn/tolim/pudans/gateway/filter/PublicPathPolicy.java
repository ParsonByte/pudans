package cn.tolim.pudans.gateway.filter;

import org.springframework.http.HttpMethod;

import java.util.HashSet;
import java.util.Set;

public final class PublicPathPolicy {

    public static final String API_BASE_PATH = "/apis/pudans.tolim.cn/v1alpha1";
    public static final String API_PATH_PREFIX = API_BASE_PATH + "/";
    public static final String IAM_PATH_PREFIX = API_PATH_PREFIX + "iam/";

    public static final String LOGIN_PATH = IAM_PATH_PREFIX + "auth/login";
    public static final String LOGIN_CONFIG_PATH = IAM_PATH_PREFIX + "auth/login/config";
    public static final String LOGOUT_PATH = IAM_PATH_PREFIX + "auth/logout";
    public static final String MFA_CHALLENGE_PATH = IAM_PATH_PREFIX + "auth/mfa/challenge";
    public static final String MFA_VERIFY_PATH = IAM_PATH_PREFIX + "auth/mfa/verify";
    public static final String MFA_WEBAUTHN_OPTIONS_PATH =
            IAM_PATH_PREFIX + "auth/mfa/webauthn/options";
    public static final String MFA_WEBAUTHN_VERIFY_PATH =
            IAM_PATH_PREFIX + "auth/mfa/webauthn/verify";
    public static final String FORGOT_PASSWORD_PATH = IAM_PATH_PREFIX + "auth/password/forgot";
    public static final String RESET_PASSWORD_PATH = IAM_PATH_PREFIX + "auth/password/reset";

    public static final String CSRF_PATH = IAM_PATH_PREFIX + "csrf";
    public static final String CAPTCHA_PATH = IAM_PATH_PREFIX + "auth/captcha";
    public static final String LEGACY_CAPTCHA_PATH = IAM_PATH_PREFIX + "captcha";

    public static final String SESSION_VALIDATE_PATH = IAM_PATH_PREFIX + "session/validate";
    public static final String SESSIONS_VALIDATE_PATH = IAM_PATH_PREFIX + "sessions/validate";

    public static final String TICKET_ISSUE_PATH = IAM_PATH_PREFIX + "ticket/issue";
    public static final String TICKETS_ISSUE_PATH = IAM_PATH_PREFIX + "tickets/issue";
    public static final String TICKET_EXCHANGE_PATH = IAM_PATH_PREFIX + "ticket/exchange";
    public static final String TICKETS_EXCHANGE_PATH = IAM_PATH_PREFIX + "tickets/exchange";

    public static final String IAM_APP_CODE = "iam";

    public static final String PORTAL_PATH = IAM_PATH_PREFIX + "portal";
    public static final String PORTAL_PATH_PREFIX = PORTAL_PATH + "/";
    public static final String PORTAL_LOGIN_CONFIG_PATH = PORTAL_PATH_PREFIX + "login-config";
    public static final String PORTAL_LOGOUT_URL_PATH = PORTAL_PATH_PREFIX + "logout-url";

    public static final String ACTUATOR_PATH = API_PATH_PREFIX + "actuator";
    public static final String HEALTH_PATH = ACTUATOR_PATH + "/health";
    public static final String INFO_PATH = ACTUATOR_PATH + "/info";

    public static final String HOME_PATH = "/";
    public static final String LOGIN_PAGE_PATH = "/login";
    public static final String LOGIN_PAGE_PREFIX = LOGIN_PAGE_PATH + "/";
    public static final String APP_PATH_PREFIX = "/apps/";
    public static final String SSO_PATH_SEGMENT = "sso";
    public static final String SSO_CALLBACK_SEGMENT = "callback";
    public static final String SSO_BACKCHANNEL_LOGOUT_SEGMENT = "backchannel-logout";

    private static final Set<String> PUBLIC_POST_PATHS = buildPublicPostPaths();
    private static final Set<String> PUBLIC_GET_PATHS = buildPublicGetPaths();
    private static final Set<String> STATIC_ASSET_PATHS = Set.of(
            "/favicon.ico",
            "/robots.txt",
            "/manifest.json",
            "/site.webmanifest");
    private static final Set<String> STATIC_ASSET_PREFIXES = Set.of("/assets/", "/static/");

    private PublicPathPolicy() {
    }

    private static Set<String> buildPublicPostPaths() {
        Set<String> paths = new HashSet<>();
        paths.add(LOGIN_PATH);
        paths.add(LOGOUT_PATH);
        paths.add(MFA_CHALLENGE_PATH);
        paths.add(MFA_VERIFY_PATH);
        paths.add(MFA_WEBAUTHN_OPTIONS_PATH);
        paths.add(MFA_WEBAUTHN_VERIFY_PATH);
        paths.add(FORGOT_PASSWORD_PATH);
        paths.add(RESET_PASSWORD_PATH);
        paths.add(SESSION_VALIDATE_PATH);
        paths.add(SESSIONS_VALIDATE_PATH);
        paths.add(TICKET_ISSUE_PATH);
        paths.add(TICKETS_ISSUE_PATH);
        paths.add(TICKET_EXCHANGE_PATH);
        paths.add(TICKETS_EXCHANGE_PATH);
        return Set.copyOf(paths);
    }

    private static Set<String> buildPublicGetPaths() {
        Set<String> paths = new HashSet<>();
        paths.add(CSRF_PATH);
        paths.add(CAPTCHA_PATH);
        paths.add(LEGACY_CAPTCHA_PATH);
        paths.add(LOGIN_CONFIG_PATH);
        paths.add(PORTAL_LOGIN_CONFIG_PATH);
        paths.add(PORTAL_LOGOUT_URL_PATH);
        return Set.copyOf(paths);
    }

    public static boolean isPublic(HttpMethod method, String path) {
        return isPublicPage(path) || isPublicApi(method, path);
    }

    public static boolean isPublicApi(HttpMethod method, String path) {
        if (HEALTH_PATH.equals(path) || INFO_PATH.equals(path)) {
            return true;
        }
        if (!path.startsWith(IAM_PATH_PREFIX)) {
            return false;
        }
        if (HttpMethod.POST.equals(method)) {
            return PUBLIC_POST_PATHS.contains(path);
        }
        if (HttpMethod.GET.equals(method)) {
            return PUBLIC_GET_PATHS.contains(path);
        }
        return false;
    }

    public static boolean isPublicPage(String path) {
        return HOME_PATH.equals(path)
                || LOGIN_PAGE_PATH.equals(path)
                || path.startsWith(LOGIN_PAGE_PREFIX)
                || isStaticAsset(path)
                || isSsoCallback(path);
    }

    public static boolean isStaticAsset(String path) {
        if (STATIC_ASSET_PATHS.contains(path)) {
            return true;
        }
        for (String prefix : STATIC_ASSET_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSsoCallback(String path) {
        if (!path.startsWith(APP_PATH_PREFIX)) {
            return false;
        }
        String[] segments = path.split("/");
        return segments.length == 5
                && "apps".equals(segments[1])
                && SSO_PATH_SEGMENT.equals(segments[3])
                && (SSO_CALLBACK_SEGMENT.equals(segments[4])
                || SSO_BACKCHANNEL_LOGOUT_SEGMENT.equals(segments[4]));
    }

    public static boolean isAppRoute(String path) {
        return path.startsWith(APP_PATH_PREFIX) && path.length() > APP_PATH_PREFIX.length();
    }

    public static boolean isPageRoute(String path) {
        return isPublicPage(path) || isAppRoute(path);
    }

    public static boolean isRoutedPath(String path) {
        return path.startsWith(API_PATH_PREFIX) || isPageRoute(path);
    }

    public static String resolveAppCode(String path) {
        if (isAppRoute(path)) {
            String rest = path.substring(APP_PATH_PREFIX.length());
            int slash = rest.indexOf('/');
            String code = slash < 0 ? rest : rest.substring(0, slash);
            return code.isBlank() ? null : code;
        }
        if (path.startsWith(API_PATH_PREFIX)) {
            String[] segments = path.substring(API_PATH_PREFIX.length()).split("/");
            if (segments.length >= 2 && !segments[0].isBlank()) {
                return segments[0];
            }
        }
        return null;
    }
}