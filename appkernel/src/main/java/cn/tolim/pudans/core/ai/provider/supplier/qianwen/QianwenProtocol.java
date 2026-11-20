package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import java.util.List;
import java.util.Set;

public final class QianwenProtocol {

    public static final Set<AiProvider.Protocol> PROTOCOLS = Set.of(AiProvider.Protocol.QIANWEN);

    public static final String CHAT_PATH = "/compatible-mode/v1/chat/completions";

    public static final String MULTIMODAL_PATH =
            "/api/v1/services/aigc/multimodal-generation/generation";

    public static final String VIDEO_PATH = "/api/v1/services/aigc/video-generation/video-synthesis";

    public static final String THREE_D_PATH =
            "/api/v1/services/aigc/video-generation/3d-generation";

    public static final String SPEECH_PATH = "/api/v1/services/audio/tts/SpeechSynthesizer";

    public static final String ASR_PATH = "/api/v1/services/audio/asr/transcription";

    public static final String MUSIC_PATH = "/api/v1/services/audio/music/generation";

    public static final String TASK_PATH = "/api/v1/tasks/";

    private static final String WEB_SOCKET_PREFIX = "/api-ws/v1/";

    private static final List<String> PATH_PREFIXES = List.of(
            "api/v1/", "api/v2/", "compatible-mode/", "compatible-api/", "apps/");

    private static final List<String> COMPATIBLE_PREFIXES = List.of(
            "chat/completions",
            "responses",
            "files",
            "batches",
            "conversations",
            "fine-tunes",
            "embeddings",
            "images/generations");

    private static final List<String> COMPATIBLE_API_PREFIXES = List.of("reranks");

    private QianwenProtocol() {}

    public static boolean isQianwen(AiProvider.Protocol protocol) {
        return PROTOCOLS.contains(protocol);
    }

    public static String normalizePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("千问代理路径不能为空");
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
            throw new IllegalArgumentException("千问代理路径不合法。");
        }
        for (var prefix : PATH_PREFIXES) {
            if (value.startsWith(prefix)) {
                return "/" + value;
            }
        }
        for (var prefix : COMPATIBLE_PREFIXES) {
            if (value.equals(prefix) || value.startsWith(prefix + "/")) {
                return "/compatible-mode/v1/" + value;
            }
        }
        for (var prefix : COMPATIBLE_API_PREFIXES) {
            if (value.equals(prefix) || value.startsWith(prefix + "/")) {
                return "/compatible-api/v1/" + value;
            }
        }
        return "/api/v1/" + value;
    }

    public static String normalizeWebSocketPath(String path) {
        if (path == null || path.isBlank()) {
            return WEB_SOCKET_PREFIX + "realtime";
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
            throw new IllegalArgumentException("千问 WebSocket 路径不合法。");
        }
        if (value.startsWith("api-ws/")) {
            return "/" + value;
        }
        return WEB_SOCKET_PREFIX + value;
    }
}
