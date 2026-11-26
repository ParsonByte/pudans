package cn.tolim.pudans.core.ai.provider.minimax;

import cn.tolim.pudans.contract.ai.AiModelType;
import cn.tolim.pudans.core.ai.provider.AiProviderModel;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MiniMaxCatalog {

    private static final String SOURCE = "provider-documented";

    private static final List<AiProviderModel> LANGUAGE_MODELS = List.of(
            language(
                    "MiniMax-M3.1-Flash",
                    "MiniMax M3.1 Flash",
                    "原生多模态、1M 上下文的 Frontier Coding 模型，思考深度可调。",
                    "reasoning,tools,vision",
                    "text,image,video",
                    "text",
                    1_000_000,
                    524_288),
            language(
                    "MiniMax-M3",
                    "MiniMax M3",
                    "面向 Agent 推理、工具调用、代码、多模态输入和长上下文任务。",
                    "reasoning,tools,vision",
                    "text,image,video",
                    "text",
                    1_000_000,
                    524_288),
            language(
                    "MiniMax-M2.7",
                    "MiniMax M2.7",
                    "开启模型的自我迭代，面向 Agent 与复杂任务。",
                    "",
                    "",
                    "",
                    null,
                    null),
            language(
                    "MiniMax-M2.7-highspeed",
                    "MiniMax M2.7 高速版",
                    "MiniMax-M2.7 的高速版本。",
                    "",
                    "",
                    "",
                    null,
                    null),
            language(
                    "MiniMax-M2.5",
                    "MiniMax M2.5",
                    "在编程、工具调用、搜索和办公场景达到行业 SOTA。",
                    "",
                    "",
                    "",
                    null,
                    null),
            language(
                    "MiniMax-M2.5-highspeed",
                    "MiniMax M2.5 高速版",
                    "MiniMax-M2.5 的高速版本。",
                    "",
                    "",
                    "",
                    null,
                    null),
            language(
                    "MiniMax-M2.1",
                    "MiniMax M2.1",
                    "多语言编程专家，全面升级复杂编程体验。",
                    "",
                    "",
                    "",
                    null,
                    null),
            language(
                    "MiniMax-M2.1-highspeed",
                    "MiniMax M2.1 高速版",
                    "MiniMax-M2.1 的高速版本。",
                    "",
                    "",
                    "",
                    null,
                    null),
            language(
                    "MiniMax-M2",
                    "MiniMax M2",
                    "专为高效编码与 Agent 工作流打造。",
                    "",
                    "",
                    "",
                    null,
                    null));

    private static final List<AiProviderModel> MEDIA_MODELS = List.of(
            model(
                    "MiniMax-H3",
                    "MiniMax H3",
                    "多模态视频生成，支持文生、图生、首尾帧与参考素材。",
                    AiModelType.VIDEO,
                    "text,image,video,audio",
                    "video",
                    "video-generation"),
            model(
                    "MiniMax-H3-Highspeed",
                    "MiniMax H3 高速版",
                    "视频生成高速版本，兼顾质量与速度。",
                    AiModelType.VIDEO,
                    "text,image,video,audio",
                    "video",
                    "video-generation"),
            model(
                    "MiniMax-H3-Lite",
                    "MiniMax H3 轻量版",
                    "轻量视频生成模型。",
                    AiModelType.VIDEO,
                    "text,image,video,audio",
                    "video",
                    "video-generation"),
            model(
                    "MiniMax-H3-Max",
                    "MiniMax H3 极速版",
                    "强调生成速度的视频模型。",
                    AiModelType.VIDEO,
                    "text,image,video,audio",
                    "video",
                    "video-generation"),
            model(
                    "MiniMax-Hailuo-2.3",
                    "MiniMax Hailuo 2.3",
                    "视频生成模型，支持文生与图生视频。",
                    AiModelType.VIDEO,
                    "text,image",
                    "video",
                    "video-generation"),
            model(
                    "MiniMax-Hailuo-2.3-Fast",
                    "MiniMax Hailuo 2.3 Fast",
                    "快速图生视频模型。",
                    AiModelType.VIDEO,
                    "text,image",
                    "video",
                    "video-generation"),
            model(
                    "MiniMax-Hailuo-02",
                    "MiniMax Hailuo 02",
                    "视频生成模型，支持 1080P 输出。",
                    AiModelType.VIDEO,
                    "text,image",
                    "video",
                    "video-generation"),
            model(
                    "MiniMax-H3-Image",
                    "MiniMax H3 Image",
                    "图片生成与编辑，支持 1 到 16 张参考图。",
                    AiModelType.IMAGE,
                    "text,image",
                    "image",
                    "image-generation"),
            model(
                    "MiniMax-H3-Image-Highspeed",
                    "MiniMax H3 Image 高速版",
                    "图片生成与编辑的高速版本。",
                    AiModelType.IMAGE,
                    "text,image",
                    "image",
                    "image-generation"),
            model(
                    "image-01",
                    "MiniMax Image 01",
                    "文生图与图生图图片生成模型。",
                    AiModelType.IMAGE,
                    "text,image",
                    "image",
                    "image-generation"),
            model(
                    "image-01-live",
                    "MiniMax Image 01 Live",
                    "支持文生图与画风设置。",
                    AiModelType.IMAGE,
                    "text,image",
                    "image",
                    "image-generation"),
            model(
                    "speech-2.8-hd",
                    "MiniMax Speech 2.8 HD",
                    "语音合成 HD 模型，情绪渲染融合语气词。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "speech-2.8-turbo",
                    "MiniMax Speech 2.8 Turbo",
                    "语音合成 Turbo 模型，生成速度更快、音质更自然。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "speech-2.6-hd",
                    "MiniMax Speech 2.6 HD",
                    "语音合成 HD 模型，韵律与音质表现出色。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "speech-2.6-turbo",
                    "MiniMax Speech 2.6 Turbo",
                    "语音合成 Turbo 模型，低时延、响应灵敏。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "speech-02-hd",
                    "MiniMax Speech 02 HD",
                    "语音合成 HD 模型，韵律稳定、复刻相似度高。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "speech-02-turbo",
                    "MiniMax Speech 02 Turbo",
                    "语音合成 Turbo 模型，小语种能力增强。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "speech-01-hd",
                    "MiniMax Speech 01 HD",
                    "第一代语音合成 HD 模型。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "speech-01-turbo",
                    "MiniMax Speech 01 Turbo",
                    "第一代语音合成 Turbo 模型。",
                    AiModelType.AUDIO,
                    "text",
                    "audio",
                    "audio-generation"),
            model(
                    "asr-1.0",
                    "MiniMax ASR 1.0",
                    "语音识别模型，支持多语言转写与时间戳。",
                    AiModelType.AUDIO,
                    "audio",
                    "text",
                    "asr"));

    private MiniMaxCatalog() {}

    // 模型列表接口只返回语言模型，媒体模型按官方模型清单补充。
    static List<AiProviderModel> mediaModels() {
        return MEDIA_MODELS;
    }

    // 用文档元数据补全模型列表返回的语言模型。
    static AiProviderModel enrich(AiProviderModel model) {
        var metadata = LANGUAGE_MODELS.stream()
                .filter(entry -> entry.modelId().equalsIgnoreCase(model.modelId()))
                .findFirst()
                .orElse(null);
        if (metadata == null) {
            return model;
        }
        var catalog = new LinkedHashMap<String, Object>();
        catalog.putAll(metadata.catalog());
        catalog.putAll(model.catalog());
        return new AiProviderModel(
                model.modelId(),
                metadata.displayName(),
                metadata.modelType(),
                model.source(),
                metadata.contextWindow(),
                metadata.maxOutputTokens(),
                metadata.inputModalities(),
                metadata.outputModalities(),
                metadata.features(),
                Map.copyOf(catalog));
    }

    private static AiProviderModel language(
            String modelId,
            String displayName,
            String description,
            String features,
            String inputModalities,
            String outputModalities,
            Integer contextWindow,
            Integer maxOutputTokens) {
        var catalog = new LinkedHashMap<String, Object>();
        catalog.put("provider", MiniMaxProvider.PROVIDER);
        catalog.put("model", modelId);
        catalog.put("source", SOURCE);
        catalog.put("description", description);
        return new AiProviderModel(
                modelId,
                displayName,
                AiModelType.CHAT,
                SOURCE,
                contextWindow,
                maxOutputTokens,
                split(inputModalities),
                split(outputModalities),
                split(features),
                Map.copyOf(catalog));
    }

    private static AiProviderModel model(
            String modelId,
            String displayName,
            String description,
            AiModelType type,
            String inputModalities,
            String outputModalities,
            String features) {
        var catalog = new LinkedHashMap<String, Object>();
        catalog.put("provider", MiniMaxProvider.PROVIDER);
        catalog.put("model", modelId);
        catalog.put("source", SOURCE);
        catalog.put("description", description);
        return new AiProviderModel(
                modelId,
                displayName,
                type,
                SOURCE,
                null,
                null,
                split(inputModalities),
                split(outputModalities),
                split(features),
                Map.copyOf(catalog));
    }

    private static List<String> split(String values) {
        return values == null || values.isBlank() ? List.of() : List.of(values.split(","));
    }
}
