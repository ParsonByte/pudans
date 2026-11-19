package cn.tolim.pudans.core.ai.conversation;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.NewConversation;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiMessageRepository.NewMessage;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiConversationService {

    private static final String ROLE_USER = "user";

    private static final String ROLE_ASSISTANT = "assistant";

    private static final String CONTENT_TYPE_TEXT = "TEXT";

    private static final String CODE_CONVERSATION_NOT_FOUND = "ai-conversation-not-found";

    private static final String CODE_CONVERSATION_FORBIDDEN = "ai-conversation-forbidden";

    private final AiConversationRepository conversationRepository;

    private final AiMessageRepository messageRepository;

    public Mono<Conversation> requireOwned(long conversationId, String userId) {
        Assert.isTrue(conversationId > 0, "会话 ID 必须大于零");
        Assert.hasText(userId, "userId 不能为空");
        return conversationRepository
                .findById(conversationId)
                .switchIfEmpty(Mono.error(conversationNotFound()))
                .filter(conversation -> userId.equals(conversation.userId()))
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.FORBIDDEN, CODE_CONVERSATION_FORBIDDEN, "无权访问该会话。")));
    }

    public Flux<Conversation> list(String userId, long beforeId, int limit) {
        Assert.hasText(userId, "userId 不能为空");
        Assert.isTrue(beforeId > 0, "分页游标必须大于零");
        Assert.isTrue(limit > 0, "分页数量必须大于零");
        return conversationRepository.listByUser(userId, beforeId, limit);
    }

    public Flux<Message> history(Conversation conversation, long afterId, int limit) {
        Assert.notNull(conversation, "会话不能为空");
        Assert.isTrue(afterId >= 0, "消息游标不能为负数");
        Assert.isTrue(limit > 0, "历史消息数量必须大于零");
        return messageRepository.listMessages(conversation.id(), afterId, limit);
    }

    public Mono<Void> delete(Conversation conversation) {
        Assert.notNull(conversation, "会话不能为空");
        return conversationRepository
                .softDelete(conversation.id(), conversation.userId())
                .flatMap(deleted -> Boolean.TRUE.equals(deleted)
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(conversationNotFound()));
    }

    public Mono<Conversation> createOrGet(NewConversation newConversation) {
        Assert.notNull(newConversation, "会话创建参数不能为空");
        return findBySession(newConversation)
                // 并发创建由唯一键兜底，冲突后回读已存在的会话。
                .switchIfEmpty(Mono.defer(() -> conversationRepository.create(newConversation)
                        .switchIfEmpty(Mono.defer(() -> findBySession(newConversation)))))
                .switchIfEmpty(Mono.error(new IllegalStateException("会话创建失败。")));
    }

    public Mono<Message> appendUserMessage(
            Conversation conversation, int turnIndex, int messageIndex, String content, @Nullable String requestId) {
        Assert.notNull(conversation, "会话不能为空");
        Assert.hasText(content, "消息内容不能为空");
        return appendMessage(conversation, turnIndex, messageIndex, ROLE_USER, content, requestId, null, null);
    }

    public Mono<Message> appendAssistantMessage(
            Conversation conversation,
            int turnIndex,
            int messageIndex,
            String content,
            @Nullable String requestId,
            @Nullable String modelCode,
            @Nullable Integer tokenCount) {
        Assert.notNull(conversation, "会话不能为空");
        Assert.notNull(content, "消息内容不能为空");
        return appendMessage(
                conversation, turnIndex, messageIndex, ROLE_ASSISTANT, content, requestId, modelCode, tokenCount);
    }

    public Mono<Integer> markPending(Conversation conversation, int messageCount) {
        Assert.notNull(conversation, "会话不能为空");
        Assert.isTrue(messageCount > 0, "待处理消息数必须大于零");
        return conversationRepository
                .addPending(conversation.id(), messageCount)
                .switchIfEmpty(Mono.error(conversationNotFound()));
    }

    public Mono<Integer> reducePending(Conversation conversation, int delta) {
        Assert.notNull(conversation, "会话不能为空");
        Assert.isTrue(delta > 0, "待处理消息数必须大于零");
        return conversationRepository.reducePending(conversation.id(), delta);
    }

    public Flux<Message> history(Conversation conversation, int limit) {
        return history(conversation, 0, limit);
    }

    public Mono<Boolean> advanceL1Cursor(Conversation conversation, long messageId) {
        Assert.notNull(conversation, "会话不能为空");
        Assert.isTrue(messageId > 0, "消息 ID 必须大于零");
        return conversationRepository.advanceL1Cursor(conversation.id(), messageId);
    }

    public Mono<Boolean> advanceL2Cursor(Conversation conversation, long l1Id) {
        Assert.notNull(conversation, "会话不能为空");
        Assert.isTrue(l1Id > 0, "L1 ID 必须大于零");
        return conversationRepository.advanceL2Cursor(conversation.id(), l1Id);
    }

    private Mono<Conversation> findBySession(NewConversation newConversation) {
        return conversationRepository.findBySession(
                newConversation.spaceId(),
                newConversation.userId(),
                newConversation.agentSource(),
                newConversation.sessionKey());
    }

    private Mono<Message> appendMessage(
            Conversation conversation,
            int turnIndex,
            int messageIndex,
            String role,
            String content,
            @Nullable String requestId,
            @Nullable String modelCode,
            @Nullable Integer tokenCount) {        var newMessage = new NewMessage(
                conversation.id(),
                conversation.spaceId(),
                conversation.userId(),
                conversation.teamId(),
                conversation.agentId(),
                conversation.sessionKey(),
                conversation.taskId(),
                requestId,
                turnIndex,
                messageIndex,
                role,
                content,
                CONTENT_TYPE_TEXT,
                tokenCount,
                modelCode);
        return messageRepository.appendMessage(newMessage);
    }

    private static ApiException conversationNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, CODE_CONVERSATION_NOT_FOUND, "会话不存在。");
    }
}
