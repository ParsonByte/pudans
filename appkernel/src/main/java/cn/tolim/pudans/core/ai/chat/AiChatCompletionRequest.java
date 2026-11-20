package cn.tolim.pudans.core.ai.chat;

import java.util.List;
import org.jspecify.annotations.Nullable;

public record AiChatCompletionRequest(
        @Nullable Long conversationId,
        @Nullable String mode,
        @Nullable String routeGroup,
        @Nullable String teamId,
        @Nullable String agentId,
        @Nullable List<String> kbCodes,
        List<Message> messages,
        @Nullable Integer maxOutputTokens,
        @Nullable Double temperature,
        @Nullable Double topP,
        @Nullable String idempotencyKey,
        @Nullable List<AiChatAttachment> attachments,
        @Nullable String command,
        @Nullable String commandInput,
        @Nullable String model,
        @Nullable Boolean regenerate) {

    public boolean isRegenerate() {
        return Boolean.TRUE.equals(regenerate);
    }

    public AiChatCompletionRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("消息列表不能为空。");
        }
        messages = List.copyOf(messages);
        if (attachments != null && !attachments.isEmpty()) {
            attachments = List.copyOf(attachments);
        }
        if (maxOutputTokens != null && maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens 必须大于零。");
        }
    }

    public record Message(String role, String content) {

        public Message {
            if (role == null || role.isBlank()) {
                throw new IllegalArgumentException("消息角色不能为空。");
            }
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("消息内容不能为空。");
            }
        }
    }

    public record AiChatAttachment(
            String storageFileId,
            String type,
            @Nullable String name,
            @Nullable String contentType,
            @Nullable Long size) {

        public AiChatAttachment {
            if (storageFileId == null || storageFileId.isBlank()) {
                throw new IllegalArgumentException("附件文件标识不能为空。");
            }
            type = type == null || type.isBlank() ? "FILE" : type;
        }

        public AiChatAttachment(String storageFileId, String type) {
            this(storageFileId, type, null, null, null);
        }
    }
}
