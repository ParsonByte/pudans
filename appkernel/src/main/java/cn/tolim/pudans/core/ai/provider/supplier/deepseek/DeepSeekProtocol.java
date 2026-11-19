package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import java.util.Set;

public final class DeepSeekProtocol {

    public static final String NAME = "DeepSeek";

    static final String MESSAGES_PATH = "/anthropic/v1/messages";

    static final String CHAT_PATH = "/chat/completions";

    static final String RESPONSES_PATH = "/responses";

    static final String FILES_PATH = "/files";

    static final String API_KEY_HEADER = "x-api-key";

    static final String BETA_HEADER = "anthropic-beta";

    static final String FILES_BETA = "files-api-2025-04-14";

    public static final Set<String> FILE_PURPOSES =
            Set.of("user_data", "text_recognition", "rich_layout_recognition");

    private DeepSeekProtocol() {}

    public static boolean isDeepSeek(AiProvider.Protocol protocol) {
        return protocol == AiProvider.Protocol.DEEPSEEK
                || protocol == AiProvider.Protocol.DEEPSEEK_CHAT
                || protocol == AiProvider.Protocol.DEEPSEEK_RESPONSES;
    }
}
