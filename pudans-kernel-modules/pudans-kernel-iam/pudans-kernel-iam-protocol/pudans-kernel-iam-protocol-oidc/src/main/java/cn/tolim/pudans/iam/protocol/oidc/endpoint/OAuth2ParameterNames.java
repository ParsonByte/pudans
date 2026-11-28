package cn.tolim.pudans.iam.protocol.oidc.endpoint;


public final class OAuth2ParameterNames {
    // grant_type - used in Access Token Request.
    public static final String GRANT_TYPE = "grant_type";

    // response_type - used in Authorization Request.
    public static final String RESPONSE_TYPE = "response_type";

    // client_id - used in Authorization Request and Access Token Request.
    public static final String CLIENT_ID = "client_id";

    // client_secret - used in Access Token Request.
    public static final String CLIENT_SECRET = "client_secret";

    // client_assertion_type - used in Access Token Request.
    public static final String CLIENT_ASSERTION_TYPE = "client_assertion_type";

    // client_assertion - used in Access Token Request.
    public static final String CLIENT_ASSERTION = "client_assertion";

    // assertion - used in Access Token Request.
    // Since 5.5
    public static final String ASSERTION = "assertion";

    // redirect_uri - used in Authorization Request and Access Token Request.
    public static final String REDIRECT_URI = "redirect_uri";

    // scope - used in Authorization Request, Authorization Response, Access Token
    // Request and Access Token Response.
    public static final String SCOPE = "scope";

    // state - used in Authorization Request and Authorization Response.
    public static final String STATE = "state";

    // code - used in Authorization Response and Access Token Request.
    public static final String CODE = "code";

    // access_token - used in Authorization Response and Access Token Response.
    public static final String ACCESS_TOKEN = "access_token";

    // token_type - used in Authorization Response and Access Token Response.
    public static final String TOKEN_TYPE = "token_type";

    // expires_in - used in Authorization Response and Access Token Response.
    public static final String EXPIRES_IN = "expires_in";

    // refresh_token - used in Access Token Request and Access Token Response.
    public static final String REFRESH_TOKEN = "refresh_token";

    // username - used in Access Token Request.
    public static final String USERNAME = "username";

    // password - used in Access Token Request.
    public static final String PASSWORD = "password";

    // error - used in Authorization Response and Access Token Response.
    public static final String ERROR = "error";

    // error_description - used in Authorization Response and Access Token
    // Response.
    public static final String ERROR_DESCRIPTION = "error_description";

    // error_uri - used in Authorization Response and Access Token Response.
    public static final String ERROR_URI = "error_uri";

    // Non-standard parameter (used internally).
    public static final String REGISTRATION_ID = "registration_id";

    // token - used in Token Revocation Request.
    public static final String TOKEN = "token";

    // id_token - used in Authorization Response and Access Token Request.
    public static final String ID_TOKEN = "id_token";

    // id_token - used in Authorization Response and Access Token Request.
    public static final String TOKEN_ID_TOKEN = "token id_token";

    // token_type_hint - used in Token Revocation Request.
    public static final String TOKEN_TYPE_HINT = "token_type_hint";

    // device_code - used in Device Authorization Response and Device Access Token
    public static final String DEVICE_CODE = "device_code";

    // user_code - used in Device Authorization Response.
    public static final String USER_CODE = "user_code";

    // verification_uri - used in Device Authorization Response.
    public static final String VERIFICATION_URI = "verification_uri";

    // verification_uri_complete - used in Device Authorization Response.
    public static final String VERIFICATION_URI_COMPLETE = "verification_uri_complete";

    // interval - used in Device Authorization Response.
    public static final String INTERVAL = "interval";


    public static final String RESPONSE_MODE = "response_mode";
    public static final String FRAGMENT = "fragment";
    public static final String QUERY = "query";

    private OAuth2ParameterNames() {
    }
}
