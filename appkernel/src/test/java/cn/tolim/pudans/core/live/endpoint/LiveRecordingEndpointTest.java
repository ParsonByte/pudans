package cn.tolim.pudans.core.live.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.live.recording.RecordingQueries;
import cn.tolim.pudans.core.live.recording.Recordings;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class LiveRecordingEndpointTest {

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
    void activeRecordingsExposeLastWriteAndPacketTimes() {
        when(client.listAll(any(), any(), any())).thenReturn(Flux.just(recording()));

        web.get()
                .uri("/liverecordings/active")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].lastWriteAt")
                .isEqualTo("2026-11-20T10:09:00Z")
                .jsonPath("$[0].lastPacketAt")
                .isEqualTo("2026-11-20T10:08:30Z");
    }

    private static LiveRecording recording() {
        var recording = new LiveRecording();
        recording.setMetadata(new Metadata());
        recording.getMetadata().setName("rec-1");
        var spec = new LiveRecording.Spec();
        spec.setSourceName("douyin-1");
        spec.setStartedAt(Instant.parse("2026-11-20T10:00:00Z"));
        recording.setSpec(spec);
        var status = new LiveRecording.Status();
        status.setState(LiveRecordingState.RECORDING);
        status.setStartedAt(Instant.parse("2026-11-20T10:00:00Z"));
        status.setLastWriteAt(Instant.parse("2026-11-20T10:09:00Z"));
        status.setLastPacketAt(Instant.parse("2026-11-20T10:08:30Z"));
        recording.setStatus(status);
        return recording;
    }
}
