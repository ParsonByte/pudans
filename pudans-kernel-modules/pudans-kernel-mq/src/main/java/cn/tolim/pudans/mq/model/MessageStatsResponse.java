package cn.tolim.pudans.mq.model;

public record MessageStatsResponse(long totalMessages,
                                   long pendingMessages,
                                   long dispatchingMessages,
                                   long deliveredMessages,
                                   long retryingMessages,
                                   long consumedMessages,
                                   long deadLetterMessages,
                                   long cancelledMessages,
                                   long pendingDeadLetters,
                                   long replayedDeadLetters,
                                   long discardedDeadLetters,
                                   long retryingDeliveries,
                                   long deadLetterDeliveries) {
}