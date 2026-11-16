package cn.tolim.pudans.core.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.extension.audit.AuditRecord;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.audit.AuditStore;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    AuditStore auditStore;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AuditWal auditWal;

    private static AuditRecord record() {
        var record = new AuditRecord();
        var metadata = new Metadata();
        metadata.setName("audit-1");
        record.setMetadata(metadata);
        record.setTraceId("trace-1");
        record.setOperation("READ");
        record.setResult("SUCCESS");
        return record;
    }

    @Test
    void overflowedRecordsAreWrittenToTheLocalWal() {
        when(auditStore.append(any())).thenReturn(Mono.never());
        var service = new AuditService(auditStore, client, auditWal);
        service.start();

        for (var i = 0; i < 10_050; i++) {
            var record = record();
            record.getMetadata().setName("audit-" + i);
            service.record(record);
        }

        verify(auditWal, timeout(5_000).atLeastOnce()).append(any());
    }

    @Test
    void persistedRecordsAreTruncatedToTheColumnWidths() {
        when(auditStore.append(any())).thenReturn(Mono.empty());
        var service = new AuditService(auditStore, client, auditWal);
        service.start();
        var record = record();
        record.setTraceFlowId("f".repeat(500));
        record.setIp("i".repeat(500));
        record.setUserAgent("u".repeat(5_000));
        record.setRequestPath("p".repeat(5_000));
        record.setResourceName("r".repeat(2_000));

        service.record(record);

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditStore, timeout(2_000)).append(captor.capture());
        var persisted = captor.getValue();
        assertThat(persisted.getTraceFlowId()).hasSize(64);
        assertThat(persisted.getIp()).hasSize(128);
        assertThat(persisted.getUserAgent()).hasSize(1024);
        assertThat(persisted.getRequestPath()).hasSize(2048);
        assertThat(persisted.getResourceName()).hasSize(512);
        assertThat(persisted.isSyntheticFlow()).isFalse();
    }

    @Test
    void missingFlowIdIsSynthesizedAndMarked() {
        when(auditStore.append(any())).thenReturn(Mono.empty());
        var service = new AuditService(auditStore, client, auditWal);
        service.start();
        var record = record();
        record.setTraceFlowId(null);

        service.record(record);

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditStore, timeout(2_000)).append(captor.capture());
        assertThat(captor.getValue().getTraceFlowId()).isNotBlank();
        assertThat(captor.getValue().isSyntheticFlow()).isTrue();
    }
}
