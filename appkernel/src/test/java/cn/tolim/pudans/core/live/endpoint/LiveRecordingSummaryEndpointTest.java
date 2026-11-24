package cn.tolim.pudans.core.live.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.dto.LiveRecordingSummary;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.live.recording.RecordingQueries;
import cn.tolim.pudans.core.live.recording.Recordings;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LiveRecordingSummaryEndpointTest {

    @Mock
    private Recordings recordingService;

    @Mock
    private RecordingQueries recordingQueries;

    @Mock
    private ReactiveExtensionClient client;

    @Mock
    private StorageTransfer storageTransfer;

    @Mock
    private AuditLog auditLog;

    private WebTestClient web;

    @BeforeEach
    void setUp() {
        var endpoint = new LiveRecordingEndpoint(
                recordingService, recordingQueries, client, storageTransfer, auditLog);
        web = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void summaryReturnsCountsAndStorageTotals() {
        when(recordingService.summary())
                .thenReturn(Mono.just(new LiveRecordingSummary(7L, 2L, 3L, 1L, 1L, 8192L, 1024L)));

        web.get()
                .uri("/liverecordings/summary")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.total")
                .isEqualTo(7)
                .jsonPath("$.active")
                .isEqualTo(2)
                .jsonPath("$.completed")
                .isEqualTo(3)
                .jsonPath("$.failed")
                .isEqualTo(1)
                .jsonPath("$.canceled")
                .isEqualTo(1)
                .jsonPath("$.totalBytes")
                .isEqualTo(8192)
                .jsonPath("$.localBytes")
                .isEqualTo(1024);
    }
}
