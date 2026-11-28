package cn.tolim.pudans.iam.protocol.cas.constant;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE_VARIABLE;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.AUTHORIZE_PATH;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;
import static cn.tolim.pudans.iam.protocol.core.constant.ProtocolConstants.PROTOCOL_CACHE_PREFIX;


public final class CasProtocolConstants {

    // CAS protocol cache prefix
    public static final String CAS_PROTOCOL_CACHE_PREFIX = PROTOCOL_CACHE_PREFIX + "cas" + COLON;

    // CAS namespace used in validation responses
    public static final String CAS_NAMESPACE = "http://www.yale.edu/tp/cas";

    // CAS ticket cookie name
    public static final String DEFAULT_COOKIE_NAME = "CAS_TGC";

    // Service parameter name
    public static final String SERVICE_PARAMETER = "service";

    // Ticket parameter name
    public static final String TICKET_PARAMETER = "ticket";

    // Renew parameter name
    public static final String RENEW_PARAMETER = "renew";

    // Format parameter name
    public static final String FORMAT_PARAMETER = "format";

    // Ticket granting ticket prefix
    public static final String TICKET_GRANTING_TICKET_PREFIX = "TGT-";

    // Service ticket prefix
    public static final String SERVICE_TICKET_PREFIX = "ST-";

    // Configuration store key prefix
    public static final String CAS_CONFIG_KEY_PREFIX = "protocol:cas:config:";

    // Configuration store description
    public static final String CAS_CONFIG_DESCRIPTION = "CAS application protocol configuration";

    // Default service ticket lifetime in seconds
    public static final long DEFAULT_TICKET_TIME_TO_LIVE_SECONDS = 60;

    // Default ticket granting ticket lifetime in seconds
    public static final long DEFAULT_TICKET_GRANTING_TICKET_TIME_TO_LIVE_SECONDS = 28800;

    private CasProtocolConstants() {
    }

    public static final class CasEndpointConstants {

        // CAS authentication base path
        public static final String CAS_AUTHORIZE_BASE_PATH = AUTHORIZE_PATH + "/cas/"
                + APP_CODE_VARIABLE;

        // CAS login endpoint
        public static final String CAS_LOGIN_PATH = CAS_AUTHORIZE_BASE_PATH + "/login";

        // CAS logout endpoint
        public static final String CAS_LOGOUT_PATH = CAS_AUTHORIZE_BASE_PATH + "/logout";

        // CAS service ticket validation endpoint
        public static final String CAS_SERVICE_VALIDATE_PATH = CAS_AUTHORIZE_BASE_PATH
                + "/serviceValidate";

        // CAS proxy ticket validation endpoint
        public static final String CAS_PROXY_VALIDATE_PATH = CAS_AUTHORIZE_BASE_PATH
                + "/proxyValidate";

        // CAS 3.0 service ticket validation endpoint
        public static final String CAS_P3_SERVICE_VALIDATE_PATH = CAS_AUTHORIZE_BASE_PATH
                + "/p3/serviceValidate";

        // CAS 3.0 proxy ticket validation endpoint
        public static final String CAS_P3_PROXY_VALIDATE_PATH = CAS_AUTHORIZE_BASE_PATH
                + "/p3/proxyValidate";

        // CAS 1.0 ticket validation endpoint
        public static final String CAS_VALIDATE_PATH = CAS_AUTHORIZE_BASE_PATH + "/validate";

        private CasEndpointConstants() {
        }
    }
}