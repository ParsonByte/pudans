package cn.tolim.pudans.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.audit.AuditRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditFingerprintTest {

    private static AuditRecord record(String operation, String timestamp) {
        var record = new AuditRecord();
        record.setTraceFlowId("flow-1");
        record.setUid("uid-1");
        record.setOperation(operation);
        record.setResourceType("User");
        record.setResourceName("alice");
        record.setRequestMethod("GET");
        record.setRequestPath("/apis/users/alice");
        record.setResult("SUCCESS");
        record.setContext(Map.of("key", "value", "timestamp", timestamp));
        return record;
    }

    @Test
    void volatileContextDoesNotChangeTheFingerprint() {
        assertThat(AuditFingerprint.of(record("READ", "t1")))
                .isEqualTo(AuditFingerprint.of(record("READ", "t2")));
    }

    @Test
    void operationChangesTheFingerprint() {
        assertThat(AuditFingerprint.of(record("READ", "t1")))
                .isNotEqualTo(AuditFingerprint.of(record("UPDATE", "t1")));
    }
}
