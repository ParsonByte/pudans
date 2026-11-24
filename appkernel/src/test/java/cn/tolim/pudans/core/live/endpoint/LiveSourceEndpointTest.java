package cn.tolim.pudans.core.live.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.live.source.SourceHealth;
import cn.tolim.pudans.core.live.source.SourceSummaries;
import cn.tolim.pudans.core.live.source.Sources;
import cn.tolim.pudans.extension.contract.Metadata;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class LiveSourceEndpointTest {

    @Mock
    Sources sources;

    @Mock
    SourceSummaries sourceSummaries;

    @Mock
    SourceHealth sourceHealth;

    @Mock
    AuditLog auditLog;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(
                        new LiveSourceEndpoint(sources, sourceSummaries, sourceHealth, auditLog).endpoint())
                .build();
    }

    @Test
    @SuppressWarnings("unchecked")
    void deletesSourceAndRecordsAudit() {
        var source = source();
        when(sources.delete("douyin-1")).thenReturn(Mono.just(source));
        when(auditLog.recordChild(
                        any(),
                        isNull(),
                        eq("DELETE_LIVE_SOURCE"),
                        eq("LiveSource"),
                        eq("douyin-1"),
                        eq("SUCCESS"),
                        anyMap(),
                        isNull()))
                .thenReturn(Mono.empty());

        client.delete()
                .uri("/livesources/douyin-1")
                .exchange()
                .expectStatus()
                .isNoContent();

        var snapshot = ArgumentCaptor.forClass(Map.class);
        verify(auditLog)
                .recordChild(
                        any(),
                        isNull(),
                        eq("DELETE_LIVE_SOURCE"),
                        eq("LiveSource"),
                        eq("douyin-1"),
                        eq("SUCCESS"),
                        snapshot.capture(),
                        isNull());
        assertThat(snapshot.getValue())
                .containsEntry("roomId", "123456789")
                .containsEntry("alias", "测试别名")
                .containsEntry("autoRecord", true)
                .containsEntry("state", "LIVE");
    }

    @Test
    void deleteSucceedsWhenAuditFails() {
        when(sources.delete("douyin-1")).thenReturn(Mono.just(source()));
        when(auditLog.recordChild(
                        any(),
                        isNull(),
                        eq("DELETE_LIVE_SOURCE"),
                        eq("LiveSource"),
                        eq("douyin-1"),
                        eq("SUCCESS"),
                        anyMap(),
                        isNull()))
                .thenReturn(Mono.error(new IllegalStateException("audit down")));

        client.delete()
                .uri("/livesources/douyin-1")
                .exchange()
                .expectStatus()
                .isNoContent();
    }

    private static LiveSource source() {
        var source = new LiveSource();
        var metadata = new Metadata();
        metadata.setName("douyin-1");
        source.setMetadata(metadata);
        source.getSpec().setRoomId("123456789");
        source.getSpec().setAlias("测试别名");
        source.getSpec().setAutoRecord(true);
        source.getStatus().setState(LiveSourceState.LIVE);
        return source;
    }
}
