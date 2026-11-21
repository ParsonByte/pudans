package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.core.ai.gateway.protocol.AiProtocolException;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

public final class MinimaxProtocol {

    public static final String NAME = "MiniMax";

    public static final String COUNT_TOKENS_PATH = "/anthropic/v1/messages/count_tokens";

    public static final String NATIVE_CHAT_PATH = "/v1/text/chatcompletion_v2";

    public static final String INPUT_TOKENS_PATH = "/v1/responses/input_tokens";

    public static final String FILES_UPLOAD_PATH = "/v1/files/upload";

    public static final String FILES_LIST_PATH = "/v1/files/list";

    public static final String FILES_RETRIEVE_PATH = "/v1/files/retrieve";

    public static final String FILES_CONTENT_PATH = "/v1/files/retrieve_content";

    public static final String FILES_DELETE_PATH = "/v1/files/delete";

    public static final String MODELS_PATH = "/v1/models";

    public static final String ANTHROPIC_MODELS_PATH = "/anthropic/v1/models";

    public static final String TTS_WEBSOCKET_PATH = "/ws/v1/t2a_v2";

    private static final String WEBSOCKET_PREFIX = "/ws/v1/";

    public static final Set<AiProvider.Protocol> PROTOCOLS = Set.of(
            AiProvider.Protocol.MINIMAX,
            AiProvider.Protocol.MINIMAX_CHAT,
            AiProvider.Protocol.MINIMAX_NATIVE,
            AiProvider.Protocol.MINIMAX_RESPONSES);

    private MinimaxProtocol() {}

    public static boolean isMinimax(AiProvider.Protocol protocol) {
        return PROTOCOLS.contains(protocol);
    }

    public static String normalizeWebSocketPath(String path) {
        if (path == null || path.isBlank()) {
            return TTS_WEBSOCKET_PATH;
        }
        var value = path.strip();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        if (value.isEmpty()
                || value.contains("..")
                || value.contains("//")
                || value.contains("\\")
                || !value.matches("[A-Za-z0-9_./\\-]+")) {
            throw new IllegalArgumentException("MiniMax WebSocket 路径不合法。");
        }
        if (value.startsWith("ws/")) {
            return "/" + value;
        }
        return WEBSOCKET_PREFIX + value;
    }

    public static void rejectBaseResp(JsonNode root) {
        var baseResp = root == null ? null : root.get("base_resp");
        if (baseResp == null || !baseResp.isObject()) {
            return;
        }
        var statusCode = AiProviderHttpSupport.integer(baseResp, "status_code");
        if (statusCode == null || statusCode == 0) {
            return;
        }
        var message = AiProviderHttpSupport.text(baseResp, "status_msg");
        throw new AiProtocolException(
                "MiniMax 上游错误 " + statusCode + ": " + (message == null ? "未知错误" : message));
    }
}
