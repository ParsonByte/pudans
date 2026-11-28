package cn.tolim.pudans.contract.iam;

// Shared constants for IAM service-to-service contracts
public final class IamContractConstants {

    public static final String SERVICE_NAME = "pudans-iam";
    public static final String HEADER_SESSION_ID = "X-Pudans-Session-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String HEADER_REGISTRATION_TOKEN = "X-Pudans-Registration-Token";
    public static final String APP_ACCESS_ACTION_SUFFIX = ":access";
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "20";

    private IamContractConstants() {
    }
}
