package cn.tolim.pudans.mq.compensation;

import cn.tolim.pudans.communal.core.error.ApiException;
import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.enums.CompensationStatus;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.model.CompensationResult;
import cn.tolim.pudans.mq.repository.DeadLetterRepository;
import cn.tolim.pudans.mq.service.MessageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageCompensationServiceTests {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);

    private static final Clock CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private DeadLetterRepository deadLetterRepository;

    private MessageCompensationHandler handler;

    @BeforeEach
    void setUp() {
        deadLetterRepository = mock(DeadLetterRepository.class);
        handler = mock(MessageCompensationHandler.class);
        when(deadLetterRepository.save(any(DeadLetterEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void marksDeadLetterCompensatedWhenHandlerSucceeds() {
        DeadLetterEntity deadLetter = deadLetter();
        when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
        when(handler.supports("order.created")).thenReturn(true);
        MessageCompensationService service = new MessageCompensationService(deadLetterRepository,
                List.of(handler), new MessageMapper(), CLOCK);

        CompensationResult result = service.compensate("dead-letter-1");

        assertThat(result.status()).isEqualTo(CompensationStatus.COMPENSATED);
        assertThat(deadLetter.getCompensationStatus()).isEqualTo(CompensationStatus.COMPENSATED);
        assertThat(deadLetter.getCompensatedAt()).isEqualTo(NOW);
    }

    @Test
    void recordsFailureWhenHandlerThrows() throws Exception {
        DeadLetterEntity deadLetter = deadLetter();
        when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
        when(handler.supports("order.created")).thenReturn(true);
        doThrow(new RuntimeException("compensation failed")).when(handler).compensate(any());
        MessageCompensationService service = new MessageCompensationService(deadLetterRepository,
                List.of(handler), new MessageMapper(), CLOCK);

        CompensationResult result = service.compensate("dead-letter-1");

        assertThat(result.status()).isEqualTo(CompensationStatus.FAILED);
        assertThat(result.message()).isEqualTo("compensation failed");
        assertThat(deadLetter.getCompensationStatus()).isEqualTo(CompensationStatus.FAILED);
        assertThat(deadLetter.getCompensationError()).isEqualTo("compensation failed");
    }

    @Test
    void reportsNoHandlerForUnsupportedTopic() {
        DeadLetterEntity deadLetter = deadLetter();
        when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
        when(handler.supports("order.created")).thenReturn(false);
        MessageCompensationService service = new MessageCompensationService(deadLetterRepository,
                List.of(handler), new MessageMapper(), CLOCK);

        CompensationResult result = service.compensate("dead-letter-1");

        assertThat(result.status()).isEqualTo(CompensationStatus.NONE);
        assertThat(deadLetter.getCompensationStatus()).isEqualTo(CompensationStatus.NONE);
    }

    @Test
    void rejectsCompensationOfResolvedDeadLetter() {
        DeadLetterEntity deadLetter = deadLetter();
        deadLetter.setStatus(DeadLetterStatus.DISCARDED);
        when(deadLetterRepository.findById("dead-letter-1")).thenReturn(Optional.of(deadLetter));
        MessageCompensationService service = new MessageCompensationService(deadLetterRepository,
                List.of(handler), new MessageMapper(), CLOCK);

        assertThatThrownBy(() -> service.compensate("dead-letter-1"))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus())
                        .isEqualTo(409));
    }

    private DeadLetterEntity deadLetter() {
        DeadLetterEntity deadLetter = new DeadLetterEntity();
        deadLetter.setId("dead-letter-1");
        deadLetter.setMessageId("message-1");
        deadLetter.setTopic("order.created");
        deadLetter.setPayload("{\"orderId\":42}");
        deadLetter.setHeaders("{\"traceId\":\"t-1\"}");
        deadLetter.setAttempts(3);
        deadLetter.setLastError("boom");
        deadLetter.setStatus(DeadLetterStatus.PENDING);
        deadLetter.setDeadLetteredAt(NOW.minusMinutes(5));
        return deadLetter;
    }
}