package cn.tolim.pudans.iam.infra.constant;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.io.File;
import java.time.format.DateTimeFormatter;

public interface IamConstants {
    String HTTP_HEADER = "HttpHeader";
    String API_PREFIX = "/apis/pudans.tolim.cn/v1alpha1/iam";
    String CONTEXT_ENDPOINT = API_PREFIX + "/pudans_context";
    String SESSION_DETAILS = "PUDANS_SESSION_DETAILS";
    String DESKTOP = "Desktop";
    String DEFAULT_CSRF_HEADER_NAME = "pudans-csrf";
    String CONTEXT_ENDPOINT_GROUP_NAME = "System Info";
    String API_PATH = API_PREFIX;
    String DEFAULT_CSRF_COOKIE_NAME = "pudans-csrf-cookie";
    String CAPTCHA_CODE_SESSION = "PUDANS_LOGIN_CAPTCHA";
    String CACHE_LOCK_KEY_PREFIX = "lock:keys";
    String COLON = ":";
    String APP_PERMISSIONS_ALL = "APP_ALL";
    String PUDANS_LOGIN_SECRET = "PUDANS_LOGIN_SECRET";
    String PUDANS_BIND_MFA_SECRET_EXPIRE = "PUDANS_BIND_MFA_SECRET_EXPIRE";
    String PATH_SEPARATOR = "/";
    String REMEMBER_ME = "RememberMe";
    String DEFAULT_ADMIN_USERNAME = "admin";
    String POINT = ".";
    String JWT = "Jwt";
    String DEFAULT_TIME_FORMATTER_PATTERN = "HH:mm:ss";
    String BASIC = "Basic";
    String PUDANS_ENCRYPT_SECRET = "PUDANS_ENCRYPT_SECRET";
    String V1_API_PATH = API_PREFIX;
    String LOCAL = "Local Login";
    String ROOT_NODE = "root";
    String SYSTEM_DEFAULT_USER_NAME = "pudans";
    String DEFAULT_DATE_TIME_FORMATTER_PATTERN = "yyyy-MM-dd HH:mm:ss";
    String ORG_PERMISSIONS_ALL = "ORG_ALL";
    String CSRF_API_PATH = API_PREFIX + "/csrf";
    String FORGET_PASSWORD_TOKEN_ID = "PUDANS_FORGET_PASSWORD_TOKEN_ID";
    String DEFAULT_DATE_FORMATTER_PATTERN = "yyyy-MM-dd";
    String ROOT_DEPT_NAME = "PUDANS 根组织";
    String SOCIAL_SIGN_ON = "Social Sign On";
    String PUDANS_BIND_MFA_SECRET = "PUDANS_BIND_MFA_SECRET";

    String IPADDRESS_FILE_DIRECTORY = System.getProperty("user.home") + File.separator + ".pudans"
            + File.separator + "ip" + File.separator;
    String IPADDRESS_FILE_TAR = IPADDRESS_FILE_DIRECTORY + "GeoLite2-City.tar.gz";
    String IPADDRESS_FILE_PATH = IPADDRESS_FILE_DIRECTORY + "GeoLite2-City.mmdb";
    String SHA256_FILE_PATH = IPADDRESS_FILE_DIRECTORY + "GeoLite2-City.tar.gz.sha256";

    DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter
            .ofPattern(DEFAULT_DATE_TIME_FORMATTER_PATTERN);
    DateTimeFormatter DEFAULT_DATE_FORMATTER = DateTimeFormatter
            .ofPattern(DEFAULT_DATE_FORMATTER_PATTERN);

    Authentication PUDANS_INIT_AUTHENTICATION = anonymous("PUDANS_INIT",
            "PUDANS_INIT_AUTHENTICATION", "ROLE_ANONYMOUS");
    Authentication PUDANS_JOB_AUTHENTICATION = anonymous("PUDANS_JOB",
            "PUDANS_JOB_AUTHENTICATION", "ROLE_JOB");

    private static Authentication anonymous(String key, String principal, String authority) {
        return new AnonymousAuthenticationToken(key, principal,
                AuthorityUtils.createAuthorityList(authority));
    }
}
