package cn.tolim.pudans.contract.audit;

import static cn.tolim.pudans.contract.audit.AuditApiPaths.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

// Contract path consistency tests
class AuditContractPathsTests {

    @Test
    void pathsAreNestedUnderBase() {
        assertThat(RECORDS).startsWith(BASE);
        assertThat(RECORD_DETAIL).startsWith(BASE);
        assertThat(RECORDS_BY_REQUEST).startsWith(BASE);
        assertThat(RECORDS_BY_SESSION).startsWith(BASE);
        assertThat(EVENT_TYPES).startsWith(BASE);
    }
}
