package cn.tolim.pudans.iam.protocol.saml.exception;


public final class SamlErrorCodes {

    // Malformed or unsupported request
    public static final String INVALID_REQUEST = "invalid_request";

    // Missing application configuration
    public static final String CONFIG_ERROR = "config_error";

    // Request id expired or already consumed
    public static final String INVALID_REQUEST_ID = "invalid_request_id";

    // No SAML response signer is available
    public static final String SIGNING_UNAVAILABLE = "saml_signing_unavailable";

    // XML signature creation failure
    public static final String SIGNING_FAILED = "saml_signing_failed";

    // XML signature is absent where one is required
    public static final String SIGNATURE_MISSING = "saml_signature_missing";

    // XML signature is present but not valid
    public static final String SIGNATURE_INVALID = "saml_signature_invalid";

    // XML parsing failure
    public static final String XML_ERROR = "saml_xml_error";

    private SamlErrorCodes() {
    }
}