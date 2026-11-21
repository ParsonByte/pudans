package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.AiProvider;
import java.util.Set;

public final class DeepSeekProtocol {

    public static final String NAME = "DeepSeek";

    static final String FILES_PATH = "/files";

    public static final Set<String> FILE_PURPOSES =
            Set.of("user_data", "text_recognition", "rich_layout_recognition");

    private DeepSeekProtocol() {}

    public static boolean isDeepSeek(AiProvider.Protocol protocol) {
        return protocol == AiProvider.Protocol.DEEPSEEK
                || protocol == AiProvider.Protocol.DEEPSEEK_CHAT
                || protocol == AiProvider.Protocol.DEEPSEEK_RESPONSES;
    }
}
