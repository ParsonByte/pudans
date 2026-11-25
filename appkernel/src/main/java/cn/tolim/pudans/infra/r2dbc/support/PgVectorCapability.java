package cn.tolim.pudans.infra.r2dbc.support;

public record PgVectorCapability(
        boolean extensionInstalled, boolean vectorTypeAvailable, boolean usable, String reasonCode) {

    private static final String READY_CODE = "memory-vector-ready";

    private static final String EXTENSION_MISSING_CODE = "memory-vector-extension-missing";

    private static final String TYPE_MISSING_CODE = "memory-vector-type-missing";

    private static final String PROBE_FAILED_CODE = "memory-vector-probe-failed";

    static PgVectorCapability from(boolean extensionInstalled, boolean vectorTypeAvailable) {
        if (!extensionInstalled) {
            return new PgVectorCapability(false, false, false, EXTENSION_MISSING_CODE);
        }
        if (!vectorTypeAvailable) {
            return new PgVectorCapability(true, false, false, TYPE_MISSING_CODE);
        }
        return new PgVectorCapability(true, true, true, READY_CODE);
    }

    static PgVectorCapability probeFailed() {
        return new PgVectorCapability(false, false, false, PROBE_FAILED_CODE);
    }
}
