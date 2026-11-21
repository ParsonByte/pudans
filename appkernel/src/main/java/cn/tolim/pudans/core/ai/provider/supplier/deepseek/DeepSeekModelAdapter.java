package cn.tolim.pudans.core.ai.provider.supplier.deepseek;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.core.ai.model.AiModelAdapter;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DeepSeekModelAdapter implements AiModelAdapter {

    private static final Set<AiProvider.Protocol> PROTOCOLS = Set.of(
            AiProvider.Protocol.DEEPSEEK,
            AiProvider.Protocol.DEEPSEEK_CHAT,
            AiProvider.Protocol.DEEPSEEK_RESPONSES);

    private static final Set<AiModel.Capability> CAPABILITIES = Set.of(
            AiModel.Capability.OCR,
            AiModel.Capability.SPEECH_RECOGNITION,
            AiModel.Capability.SPEECH_SYNTHESIS);

    private final DeepSeekToolsClient client;

    public DeepSeekModelAdapter(DeepSeekToolsClient client) {
        this.client = client;
    }

    @Override
    public Set<AiProvider.Protocol> protocols() {
        return PROTOCOLS;
    }

    @Override
    public Set<AiModel.Capability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public Mono<AiModelResponse> invoke(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModelRequest request) {
        return switch (request.capability()) {
            case OCR -> ocr(provider, account, request);
            case SPEECH_RECOGNITION -> asr(provider, account, request);
            case SPEECH_SYNTHESIS -> tts(provider, account, request);
            default -> Mono.error(new UnsupportedOperationException(
                    "DeepSeek 不支持模型能力：" + request.capability()));
        };
    }

    private Mono<AiModelResponse> ocr(
            AiProvider provider, AiProviderAccount account, AiModelRequest request) {
        var file = fileInput(request.inputs());
        if (file == null) {
            return Mono.error(new IllegalArgumentException("OCR 需要图片或文件内容"));
        }
        var purpose = stringParam(request.params(), "purpose", "text_recognition");
        return client
                .ocr(provider, account, file.filename(), file.mediaType(), file.bytes(), purpose)
                .map(result -> {
                    var text = new StringBuilder();
                    for (var page : result.pages()) {
                        if (page.text() != null) {
                            if (!text.isEmpty()) {
                                text.append('\n');
                            }
                            text.append(page.text());
                        }
                    }
                    return new AiModelResponse(
                            result.id(),
                            null,
                            List.of(new AiModelContent.Text(text.toString())),
                            new AiModelUsage(
                                    result.usage() == null ? null : result.usage().inputTokens(),
                                    result.usage() == null ? null : result.usage().outputTokens(),
                                    null, null, null, null, null, null, null, Map.of()),
                            null,
                            null,
                            AiModelResponse.TaskStatus.SUCCEEDED);
                });
    }

    private Mono<AiModelResponse> asr(
            AiProvider provider, AiProviderAccount account, AiModelRequest request) {
        var file = fileInput(request.inputs());
        if (file == null) {
            return Mono.error(new IllegalArgumentException("语音识别需要音频字节内容"));
        }
        return client
                .asr(provider, account, file.filename(), file.mediaType(), file.bytes())
                .map(result -> new AiModelResponse(
                        result.requestId(),
                        null,
                        result.text() == null
                                ? List.of()
                                : List.of(new AiModelContent.Text(result.text())),
                        new AiModelUsage(
                                null, null, null, null, null, null,
                                result.durationMs() == null ? null : Math.max(1, result.durationMs() / 1000),
                                null, null, Map.of()),
                        null,
                        null,
                        AiModelResponse.TaskStatus.SUCCEEDED));
    }

    private Mono<AiModelResponse> tts(
            AiProvider provider, AiProviderAccount account, AiModelRequest request) {
        var text = textInput(request.inputs());
        if (text.isEmpty()) {
            return Mono.error(new IllegalArgumentException("语音合成需要文本内容"));
        }
        var voice = stringParam(request.params(), "voiceId", null);
        var format = stringParam(request.params(), "format", "wav");
        var filter = booleanParam(request.params(), "filterMarkdown", true);
        return DataBufferUtils.join(client.tts(provider, account, text, voice, format, filter))
                .map(buffer -> {
                    try {
                        var bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes);
                        return Base64.getEncoder().encodeToString(bytes);
                    } finally {
                        DataBufferUtils.release(buffer);
                    }
                })
                .map(audio -> new AiModelResponse(
                        null,
                        null,
                        List.of(new AiModelContent.Audio(
                                null, audio, "pcm".equals(format) ? "audio/pcm" : "audio/wav",
                                null, format, null)),
                        new AiModelUsage(
                                null, null, null, null, null, null, null, text.length(), null, Map.of()),
                        null,
                        null,
                        AiModelResponse.TaskStatus.SUCCEEDED));
    }

    private static FileInput fileInput(List<AiModelContent> inputs) {
        for (var input : inputs) {
            if (input instanceof AiModelContent.Image image && image.data() != null) {
                return new FileInput(
                        "image." + extension(image.mediaType()),
                        image.mediaType() == null ? "image/png" : image.mediaType(),
                        Base64.getDecoder().decode(image.data()));
            }
            if (input instanceof AiModelContent.Audio audio && audio.data() != null) {
                return new FileInput(
                        "audio." + extension(audio.mediaType()),
                        audio.mediaType() == null ? "audio/wav" : audio.mediaType(),
                        Base64.getDecoder().decode(audio.data()));
            }
            if (input instanceof AiModelContent.File file && file.data() != null) {
                return new FileInput(
                        file.filename() == null ? "file.bin" : file.filename(),
                        file.mediaType() == null ? "application/octet-stream" : file.mediaType(),
                        Base64.getDecoder().decode(file.data()));
            }
        }
        return null;
    }

    private static String textInput(List<AiModelContent> inputs) {
        var text = new StringBuilder();
        for (var input : inputs) {
            if (input instanceof AiModelContent.Text value) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(value.text());
            }
        }
        return text.toString();
    }

    private static String stringParam(Map<String, JsonNode> params, String name, String fallback) {
        var value = params == null ? null : params.get(name);
        return value != null && value.isTextual() ? value.textValue() : fallback;
    }

    private static boolean booleanParam(Map<String, JsonNode> params, String name, boolean fallback) {
        var value = params == null ? null : params.get(name);
        return value != null && value.isBoolean() ? value.booleanValue() : fallback;
    }

    private static String extension(String mediaType) {
        if (mediaType == null) {
            return "bin";
        }
        var slash = mediaType.indexOf('/');
        return slash < 0 ? "bin" : mediaType.substring(slash + 1);
    }

    private record FileInput(String filename, String mediaType, byte[] bytes) {}
}
