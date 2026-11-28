package cn.tolim.pudans.contract.job;

// Shared constants for job executor service-to-service contracts
public final class JobContractConstants {

    public static final String SERVICE_NAME = "pudans-job";

    public static final String ACTOR_HEADER = "X-Pudans-Actor";

    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "20";

    private JobContractConstants() {
    }
}