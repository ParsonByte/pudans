package cn.tolim.pudans.contract.ai.dto;

// 公共能力目录统一定义能力码和展示名称。
public enum AiProviderCapabilityCode {
    CHAT("chat", "对话"),
    REASONING("reasoning", "深度思考"),
    TOOLS("tools", "工具调用"),
    VISION("vision", "视觉识别"),
    WEB_SEARCH("web-search", "联网搜索"),
    FILES("files", "文件上传"),
    OCR("ocr", "OCR"),
    TTS("tts", "语音合成"),
    ASR("asr", "语音识别"),
    BALANCE("balance", "余额查询"),
    EMBEDDING("embedding", "向量化"),
    IMAGE("image", "图片生成"),
    VIDEO("video", "视频生成"),
    AUDIO("audio", "音频生成");

    private final String code;

    private final String label;

    AiProviderCapabilityCode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
