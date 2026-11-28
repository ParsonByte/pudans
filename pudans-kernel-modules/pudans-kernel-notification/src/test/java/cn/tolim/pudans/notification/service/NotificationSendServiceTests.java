package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.configuration.NotificationProperties;
import cn.tolim.pudans.notification.entity.NotificationDeadLetterEntity;
import cn.tolim.pudans.notification.entity.NotificationProviderEntity;
import cn.tolim.pudans.notification.entity.NotificationSendRecordEntity;
import cn.tolim.pudans.notification.enums.NotificationChannel;
import cn.tolim.pudans.notification.enums.ProviderType;
import cn.tolim.pudans.notification.enums.SendStatus;
import cn.tolim.pudans.notification.repository.NotificationDeadLetterRepository;
import cn.tolim.pudans.notification.repository.NotificationSendRecordRepository;
import cn.tolim.pudans.notification.sender.NotificationMessage;
import cn.tolim.pudans.notification.sender.NotificationProviderConfig;
import cn.tolim.pudans.notification.sender.NotificationSender;
import cn.tolim.pudans.notification.sender.SendResult;
import cn.tolim.pudans.notification.template.NotificationTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationSendServiceTests {

    private final NotificationSendRecordRepository recordRepository =
            mock(NotificationSendRecordRepository.class);
    private final NotificationDeadLetterRepository deadLetterRepository =
            mock(NotificationDeadLetterRepository.class);
    private final NotificationTemplateService templateService = mock(NotificationTemplateService.class);
    private final NotificationTemplateRenderer renderer = mock(NotificationTemplateRenderer.class);
    private final NotificationProviderSelector selector = mock(NotificationProviderSelector.class);
    private final NotificationProperties properties = new NotificationProperties();
    private final NotificationSendService service = new NotificationSendService(recordRepository,
            deadLetterRepository, templateService, renderer, selector,
            new NotificationRetryPolicy(properties));

    @Test
    void movesRecordToDeadLetterWhenLastAttemptFails() {
        NotificationSendRecordEntity record = record("r1", 1, 2);
        when(recordRepository.findLockedById("r1")).thenReturn(Optional.of(record));
        when(recordRepository.save(any(NotificationSendRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterRepository.save(any(NotificationDeadLetterEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(selector.selectForRecord(record)).thenReturn(selection(SendResult.failure("boom")));

        service.retry("r1");

        assertThat(record.getStatus()).isEqualTo(SendStatus.DEAD_LETTER);
        assertThat(record.getAttempts()).isEqualTo(2);
        assertThat(record.getNextRetryAt()).isNull();
        ArgumentCaptor<NotificationDeadLetterEntity> captor =
                ArgumentCaptor.forClass(NotificationDeadLetterEntity.class);
        verify(deadLetterRepository).save(captor.capture());
        assertThat(captor.getValue().getRecordId()).isEqualTo("r1");
        assertThat(captor.getValue().getAttempts()).isEqualTo(2);
        assertThat(captor.getValue().getLastError()).isEqualTo("boom");
    }

    @Test
    void schedulesRetryWhileAttemptsRemain() {
        NotificationSendRecordEntity record = record("r2", 0, 3);
        when(recordRepository.findLockedById("r2")).thenReturn(Optional.of(record));
        when(recordRepository.save(any(NotificationSendRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(selector.selectForRecord(record)).thenReturn(selection(SendResult.failure("temporary")));

        service.retry("r2");

        assertThat(record.getStatus()).isEqualTo(SendStatus.FAILED);
        assertThat(record.getAttempts()).isEqualTo(1);
        assertThat(record.getNextRetryAt()).isNotNull();
        assertThat(record.getLastError()).isEqualTo("temporary");
    }

    @Test
    void marksRecordAsSuccessWhenProviderAcceptsMessage() {
        NotificationSendRecordEntity record = record("r3", 0, 3);
        when(recordRepository.findLockedById("r3")).thenReturn(Optional.of(record));
        when(recordRepository.save(any(NotificationSendRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(selector.selectForRecord(record)).thenReturn(selection(
                SendResult.success("message-1", "accepted")));

        service.retry("r3");

        assertThat(record.getStatus()).isEqualTo(SendStatus.SUCCESS);
        assertThat(record.getAttempts()).isEqualTo(1);
        assertThat(record.getProviderMessageId()).isEqualTo("message-1");
        assertThat(record.getSentAt()).isNotNull();
        assertThat(record.getNextRetryAt()).isNull();
    }

    @Test
    void treatsProviderResolutionFailureAsADeliveryFailure() {
        NotificationSendRecordEntity record = record("r4", 1, 2);
        when(recordRepository.findLockedById("r4")).thenReturn(Optional.of(record));
        when(recordRepository.save(any(NotificationSendRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deadLetterRepository.save(any(NotificationDeadLetterEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(selector.selectForRecord(record)).thenThrow(new IllegalStateException("no provider"));

        service.retry("r4");

        assertThat(record.getStatus()).isEqualTo(SendStatus.DEAD_LETTER);
        assertThat(record.getLastError()).isEqualTo("no provider");
    }

    private NotificationSendRecordEntity record(String id, int attempts, int maxAttempts) {
        NotificationSendRecordEntity record = new NotificationSendRecordEntity();
        record.setId(id);
        record.setChannel(NotificationChannel.SMS);
        record.setProviderCode("aliyun");
        record.setProviderType(ProviderType.ALIYUN_SMS);
        record.setRecipient("13800000000");
        record.setStatus(SendStatus.PENDING);
        record.setAttempts(attempts);
        record.setMaxAttempts(maxAttempts);
        record.setParameters("{\"code\":\"1234\"}");
        return record;
    }

    private NotificationProviderSelector.Selection selection(SendResult result) {
        NotificationProviderEntity provider = new NotificationProviderEntity();
        provider.setCode("aliyun");
        provider.setChannel(NotificationChannel.SMS);
        provider.setProvider(ProviderType.ALIYUN_SMS);
        NotificationSender sender = new StubSender(result);
        return new NotificationProviderSelector.Selection(provider,
                new NotificationProviderConfig(Map.of()), sender);
    }

    private record StubSender(SendResult result) implements NotificationSender {

        @Override
        public NotificationChannel channel() {
            return NotificationChannel.SMS;
        }

        @Override
        public Set<ProviderType> providerTypes() {
            return Set.of(ProviderType.ALIYUN_SMS);
        }

        @Override
        public SendResult send(NotificationMessage message, NotificationProviderConfig config) {
            return result;
        }
    }
}