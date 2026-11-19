package cn.tolim.pudans.core.ai.chat;

import java.util.Set;
import org.jspecify.annotations.Nullable;

public record AiChatCommand(
        String userId,
        Set<String> roleNames,
        @Nullable String deptId,
        AiChatCompletionRequest request) {

    public AiChatCommand {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        roleNames = roleNames == null ? Set.of() : Set.copyOf(roleNames);
    }
}
