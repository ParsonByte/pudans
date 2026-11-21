package cn.tolim.pudans.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.audit.AuditRecord;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditFingerprintTest {

    private static AuditRecord record(String operation, String timestamp) {
        var record = new AuditRecord();
        record.setTraceId("trace-1");
        record.setTimestamp(Instant.parse("2026-01-01T00:00:00Z"));
        record.setIp("127.0.0.1");
        record.setUserAgent("test-agent");
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

    // 每次请求的服务端标识与时间不同，重复请求不能被合并。
    @Test
    void requestIdentityChangesTheFingerprint() {
        var first = record("READ", "t1");
        var second = record("READ", "t1");
        second.setTraceId("trace-2");

        assertThat(AuditFingerprint.of(first)).isNotEqualTo(AuditFingerprint.of(second));
    }

    @Test
    void operationChangesTheFingerprint() {
        assertThat(AuditFingerprint.of(record("READ", "t1")))
                .isNotEqualTo(AuditFingerprint.of(record("UPDATE", "t1")));
    }
}
