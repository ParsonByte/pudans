package cn.tolim.pudans.core.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.webhook.Webhook;
import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.core.audit.AuditLog;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    WebhookSecretCipher secretCipher;

    @Mock
    AuditLog auditLog;

    private WebhookEventBus eventBus;

    private WebhookDispatcher dispatcher;

    @AfterEach
    void tearDown() {
        if (dispatcher != null) {
            dispatcher.destroy();
        }
        if (eventBus != null) {
            eventBus.complete();
        }
    }

    private static Webhook webhook(String url) {
        var webhook = new Webhook();
        var metadata = new Metadata();
        metadata.setName("hook-1");
        webhook.setMetadata(metadata);
        var spec = new Webhook.Spec();
        spec.setUrl(url);
        spec.setEvents(List.of("user.created"));
        spec.setEnabled(true);
        spec.setMaxAttempts(1);
        spec.setTimeoutMillis(1000L);
        webhook.setSpec(spec);
        return webhook;
    }

    private void start(String url) {
        var hook = webhook(url);
        when(client.listAll(eq(Webhook.class), any(ListOptions.class), any()))
                .thenReturn(Flux.just(hook));
        when(client.fetch(eq(Webhook.class), eq("hook-1"))).thenReturn(Mono.just(hook));
        when(client.update(any(Webhook.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(auditLog.newRecord(anyString(), anyString(), anyString())).thenAnswer(invocation -> {
            var record = new AuditRecord();
            var metadata = new Metadata();
            metadata.setName("webhook-audit");
            record.setMetadata(metadata);
            record.setTraceId(invocation.getArgument(0));
            record.setUid(invocation.getArgument(1));
            record.setOperation(invocation.getArgument(2));
            return record;
        });
        eventBus = new WebhookEventBus();
        dispatcher = new WebhookDispatcher(
                eventBus, client, secretCipher, auditLog, new OutboundRequestValidator());
        dispatcher.afterPropertiesSet();
    }

    private void emitEvent() {
        eventBus.emit(new WebhookEvent("evt-1", "user.created", Instant.now(), null, Map.of("username", "alice")));
    }

    @Test
    void signatureIsSkippedWhenNoSecretIsConfigured() {
        assertThat(WebhookDispatcher.sign(null, "1", "{}")).isNull();
        assertThat(WebhookDispatcher.sign("", "1", "{}")).isNull();
        assertThat(WebhookDispatcher.sign("secret", "1", "{}")).startsWith("sha256=");
    }

    @Test
    void ssrfTargetIsRejectedAndAnAuditedFailureIsRecorded() {
        start("http://127.0.0.1:9999/hook");

        emitEvent();

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditLog, timeout(5_000)).record(captor.capture());
        var record = captor.getValue();
        assertThat(record.getOperation()).isEqualTo("WEBHOOK_DELIVER");
        assertThat(record.getResult()).isEqualTo("FAILURE");
        assertThat(record.getResourceType()).isEqualTo(Webhook.KIND);
        assertThat(record.getResourceName()).isEqualTo("hook-1");
        assertThat(record.getContext()).containsEntry("statusCode", 0);
        assertThat((String) record.getContext().get("error"))
                .isEqualTo(IllegalArgumentException.class.getName());
        assertThat(record.isSyntheticFlow()).isTrue();
        assertThat(record.getActionSnapshot()).contains("127.0.0.1:9999");
    }

    @Test
    void linkLocalTargetIsRejectedTheSameWay() {
        start("http://169.254.169.254/latest/meta-data");

        emitEvent();

        var captor = ArgumentCaptor.forClass(AuditRecord.class);
        verify(auditLog, timeout(5_000)).record(captor.capture());
        assertThat(captor.getValue().getResult()).isEqualTo("FAILURE");
        assertThat((String) captor.getValue().getContext().get("message"))
                .contains("不可路由");
    }

    @Test
    void failedDeliveryPersistsStatusAndFailureCountOnTheWebhook() {
        start("http://127.0.0.1:9999/hook");

        emitEvent();

        var captor = ArgumentCaptor.forClass(Webhook.class);
        verify(client, timeout(5_000)).update(captor.capture());
        var updated = captor.getValue();
        assertThat(updated.getSpec().getLastDeliveryStatus()).isEqualTo("FAILURE");
        assertThat(updated.getSpec().getLastDeliveredAt()).isNotNull();
        assertThat(updated.getSpec().getFailureCount()).isEqualTo(1);
    }
}
