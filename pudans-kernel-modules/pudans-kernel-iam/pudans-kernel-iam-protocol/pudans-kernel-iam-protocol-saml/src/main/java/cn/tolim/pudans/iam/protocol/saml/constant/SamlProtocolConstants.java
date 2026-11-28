package cn.tolim.pudans.iam.protocol.saml.constant;

import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.APP_CODE_VARIABLE;
import static cn.tolim.pudans.iam.communal.constant.ProtocolConstants.AUTHORIZE_PATH;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;
import static cn.tolim.pudans.iam.protocol.core.constant.ProtocolConstants.PROTOCOL_CACHE_PREFIX;


public final class SamlProtocolConstants {

    // SAML protocol cache prefix
    public static final String SAML_PROTOCOL_CACHE_PREFIX = PROTOCOL_CACHE_PREFIX + "saml" + COLON;

    // SAML error documentation
    public static final String SAML_ERROR_URI = "https://iam.pudans.cn";

    // SAML 2.0 protocol namespace
    public static final String SAML_PROTOCOL_NAMESPACE = "urn:oasis:names:tc:SAML:2.0:protocol";

    // SAML 2.0 assertion namespace
    public static final String SAML_ASSERTION_NAMESPACE = "urn:oasis:names:tc:SAML:2.0:assertion";

    // SAML 2.0 metadata namespace
    public static final String SAML_METADATA_NAMESPACE = "urn:oasis:names:tc:SAML:2.0:metadata";

    // XML signature namespace
    public static final String XML_SIGNATURE_NAMESPACE = "http://www.w3.org/2000/09/xmldsig#";

    // Status success code
    public static final String STATUS_SUCCESS = "urn:oasis:names:tc:SAML:2.0:status:Success";

    // Status requester error code
    public static final String STATUS_REQUESTER = "urn:oasis:names:tc:SAML:2.0:status:Requester";

    // Status responder error code
    public static final String STATUS_RESPONDER = "urn:oasis:names:tc:SAML:2.0:status:Responder";

    // Bearer subject confirmation method
    public static final String BEARER_CONFIRMATION_METHOD = "urn:oasis:names:tc:SAML:2.0:cm:bearer";

    // Password protected transport authentication context
    public static final String AUTHN_CONTEXT_PASSWORD_PROTECTED_TRANSPORT =
            "urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport";

    // Attribute name format
    public static final String ATTRIBUTE_NAME_FORMAT_BASIC =
            "urn:oasis:names:tc:SAML:2.0:attrname-format:basic";

    // AuthnRequest query parameter
    public static final String SAML_REQUEST_PARAMETER = "SAMLRequest";

    // SAML response query parameter
    public static final String SAML_RESPONSE_PARAMETER = "SAMLResponse";

    // Relay state query parameter
    public static final String RELAY_STATE_PARAMETER = "RelayState";

    // Stored request identifier parameter
    public static final String REQUEST_ID_PARAMETER = "requestId";

    // Configuration store key prefix
    public static final String SAML_CONFIG_KEY_PREFIX = "protocol:saml:config:";

    // Configuration store description
    public static final String SAML_CONFIG_DESCRIPTION = "SAML application protocol configuration";

    // Default assertion lifetime in seconds
    public static final long DEFAULT_ASSERTION_TIME_TO_LIVE_SECONDS = 300;

    // Default allowed clock skew in seconds
    public static final long DEFAULT_CLOCK_SKEW_SECONDS = 60;

    // Default authn request lifetime in seconds
    public static final long DEFAULT_AUTHN_REQUEST_TIME_TO_LIVE_SECONDS = 300;

    private SamlProtocolConstants() {
    }

    public static final class SamlEndpointConstants {

        // SAML authentication base path
        public static final String SAML_AUTHORIZE_BASE_PATH = AUTHORIZE_PATH + "/saml/"
                + APP_CODE_VARIABLE;

        // Identity provider metadata endpoint
        public static final String SAML_METADATA_PATH = SAML_AUTHORIZE_BASE_PATH + "/metadata";

        // Single sign-on endpoint
        public static final String SAML_SSO_PATH = SAML_AUTHORIZE_BASE_PATH + "/sso";

        // Single logout endpoint
        public static final String SAML_SLO_PATH = SAML_AUTHORIZE_BASE_PATH + "/slo";

        private SamlEndpointConstants() {
        }
    }
}