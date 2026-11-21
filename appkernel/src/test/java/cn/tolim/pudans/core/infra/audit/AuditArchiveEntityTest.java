package cn.tolim.pudans.core.infra.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuditArchiveEntityTest {

    @Test
    void toRecordRestoresSyntheticFlowMarkerFromContext() {
        var entity = new AuditArchiveEntity();
        entity.setId("audit-1");
        entity.setContextJson("{\"syntheticFlow\":true,\"key\":\"value\"}");
        entity.setResult("SUCCESS");

        var record = entity.toRecord();

        assertThat(record.isSyntheticFlow()).isTrue();
        assertThat(record.getContext()).doesNotContainKey("syntheticFlow");
        assertThat(record.getContext()).containsEntry("key", "value");
    }
}
