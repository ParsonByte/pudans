package cn.tolim.pudans.mq.compensation;

import cn.tolim.pudans.mq.entity.DeadLetterEntity;
import cn.tolim.pudans.mq.enums.CompensationStatus;
import cn.tolim.pudans.mq.enums.DeadLetterStatus;
import cn.tolim.pudans.mq.exception.MessageCenterException;
import cn.tolim.pudans.mq.model.CompensationResult;
import cn.tolim.pudans.mq.repository.DeadLetterRepository;
import cn.tolim.pudans.mq.service.MessageMapper;
import cn.tolim.pudans.mq.util.MessageTexts;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageCompensationService {

    private final DeadLetterRepository deadLetterRepository;

    private final List<MessageCompensationHandler> handlers;

    private final MessageMapper messageMapper;

    private final Clock clock;

    public MessageCompensationService(DeadLetterRepository deadLetterRepository,
                                      List<MessageCompensationHandler> handlers,
                                      MessageMapper messageMapper, Clock clock) {
        this.deadLetterRepository = deadLetterRepository;
        this.handlers = handlers;
        this.messageMapper = messageMapper;
        this.clock = clock;
    }

    @Transactional
    public CompensationResult compensate(String deadLetterId) {
        DeadLetterEntity deadLetter = deadLetterRepository.findById(deadLetterId)
                .orElseThrow(() -> MessageCenterException.notFound("dead letter", deadLetterId));
        if (deadLetter.getStatus() != DeadLetterStatus.PENDING) {
            throw MessageCenterException.conflict("Dead letter " + deadLetterId
                    + " cannot be compensated in status " + deadLetter.getStatus());
        }
        if (deadLetter.getCompensationStatus() == CompensationStatus.COMPENSATED) {
            throw MessageCenterException.conflict("Dead letter " + deadLetterId
                    + " was already compensated");
        }
        List<MessageCompensationHandler> supportingHandlers = handlers.stream()
                .filter(handler -> handler.supports(deadLetter.getTopic()))
                .toList();
        if (supportingHandlers.isEmpty()) {
            return new CompensationResult(deadLetterId, CompensationStatus.NONE,
                    "No compensation handler registered for topic " + deadLetter.getTopic());
        }
        CompensationContext context = new CompensationContext(deadLetter.getId(),
                deadLetter.getMessageId(), deadLetter.getTopic(), deadLetter.getEventType(),
                deadLetter.getPayload(), messageMapper.parseHeaders(deadLetter.getHeaders()),
                deadLetter.getAttempts(), deadLetter.getLastError());
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            for (MessageCompensationHandler handler : supportingHandlers) {
                handler.compensate(context);
            }
            deadLetter.markCompensated(now);
            deadLetterRepository.save(deadLetter);
            return new CompensationResult(deadLetterId, CompensationStatus.COMPENSATED, null);
        } catch (Exception exception) {
            String error = MessageTexts.isBlank(exception.getMessage())
                    ? exception.getClass().getName() : exception.getMessage();
            deadLetter.markCompensationFailed(error, now);
            deadLetterRepository.save(deadLetter);
            return new CompensationResult(deadLetterId, CompensationStatus.FAILED, error);
        }
    }
}