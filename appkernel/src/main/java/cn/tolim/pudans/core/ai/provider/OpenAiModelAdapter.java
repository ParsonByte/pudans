package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingClient;
import cn.tolim.pudans.core.ai.model.AiModelAdapter;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.rerank.AiRerankClient;
import cn.tolim.pudans.core.ai.rerank.RerankRequest;
import cn.tolim.pudans.core.ai.rerank.RerankResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class OpenAiModelAdapter implements AiModelAdapter {

    private static final Set<AiProvider.Protocol> PROTOCOLS = Set.of(AiProvider.Protocol.OPENAI_CHAT);

    private static final Set<AiModel.Capability> CAPABILITIES = Set.of(
            AiModel.Capability.IMAGE_GENERATION,
            AiModel.Capability.SPEECH_SYNTHESIS,
            AiModel.Capability.SPEECH_RECOGNITION,
            AiModel.Capability.EMBEDDING,
            AiModel.Capability.RERANK);

    private final OpenAiMediaClient client;

    private final AiEmbeddingClient embeddingClient;

    private final AiRerankClient rerankClient;

    private final ObjectMapper mapper;

    public OpenAiModelAdapter(
            OpenAiMediaClient client,
            AiEmbeddingClient embeddingClient,
            AiRerankClient rerankClient,
            ObjectMapper mapper) {
        this.client = client;
        this.embeddingClient = embeddingClient;
        this.rerankClient = rerankClient;
        this.mapper = mapper;
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
            case IMAGE_GENERATION -> image(provider, account, model, request);
            case SPEECH_SYNTHESIS -> speech(provider, account, model, request);
            case SPEECH_RECOGNITION -> transcribe(provider, account, model, request);
            case EMBEDDING -> embed(provider, account, model, request);
            case RERANK -> rerank(provider, account, model, request);
            default -> Mono.error(new UnsupportedOperationException(
                    "OpenAI 不支持模型能力：" + request.capability()));
        };
    }

    private Mono<AiModelResponse> image(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = body(model, request);
        if (!body.has("prompt")) {
            body.put("prompt", text(request.inputs()));
        }
        body.put("stream", false);
        return client.generateImage(provider, account, body).map(root -> {
            var outputs = new ArrayList<AiModelContent>();
            var data = root.get("data");
            if (data != null && data.isArray()) {
                for (var item : data) {
                    if (item.hasNonNull("url")) {
                        outputs.add(new AiModelContent.Image(
                                item.get("url").asText(), null, null, null, null));
                    } else if (item.hasNonNull("b64_json")) {
                        outputs.add(new AiModelContent.Image(
                                null, item.get("b64_json").asText(), null, null, null));
                    }
                }
            }
            return new AiModelResponse(
                    null,
                    null,
                    outputs,
                    new AiModelUsage(null, null, null, null, outputs.size(), null, null, null, null, Map.of()),
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private Mono<AiModelResponse> speech(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = body(model, request);
        body.put("input", text(request.inputs()));
        if (!body.has("voice")) {
            body.put("voice", "alloy");
        }
        var format = body.hasNonNull("response_format") ? body.get("response_format").asText() : "mp3";
        return client.synthesize(provider, account, body).map(bytes -> {
            var audio = Base64.getEncoder().encodeToString(bytes);
            return new AiModelResponse(
                    null,
                    null,
                    List.of(new AiModelContent.Audio(
                            null, audio, audioMediaType(format), null, format, null)),
                    new AiModelUsage(
                            null, null, null, null, null, null, null,
                            text(request.inputs()).length(), null, Map.of()),
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private Mono<AiModelResponse> transcribe(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var file = fileInput(request.inputs());
        if (file == null) {
            return Mono.error(new IllegalArgumentException("语音识别需要音频字节内容"));
        }
        var fields = new LinkedHashMap<String, String>();
        fields.put("model", modelCode(model));
        stringParam(request.params(), "language", value -> fields.put("language", value));
        stringParam(request.params(), "prompt", value -> fields.put("prompt", value));
        stringParam(request.params(), "response_format", value -> fields.put("response_format", value));
        return client
                .transcribe(provider, account, file.filename(), file.mediaType(), file.bytes(), fields)
                .map(root -> {
                    var text = root.hasNonNull("text") ? root.get("text").asText() : root.toString();
                    return new AiModelResponse(
                            null,
                            null,
                            List.of(new AiModelContent.Text(text)),
                            new AiModelUsage(
                                    null, null, null, null, null, null, null, null, 1, Map.of()),
                            null,
                            null,
                            AiModelResponse.TaskStatus.SUCCEEDED);
                });
    }

    private Mono<AiModelResponse> embed(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var texts = new ArrayList<String>();
        for (var input : request.inputs()) {
            if (input instanceof AiModelContent.Text text) {
                texts.add(text.text());
            } else {
                return Mono.error(new IllegalArgumentException("OpenAI 嵌入协议仅支持文本输入"));
            }
        }
        var modelCode = modelCode(model);
        return embeddingClient.embed(provider, account, modelCode, texts).map(vectors -> {
            var outputs = new ArrayList<AiModelContent>();
            for (var vector : vectors) {
                outputs.add(new AiModelContent.Vector(modelCode, vector.length, vector));
            }
            return new AiModelResponse(
                    null,
                    modelCode,
                    outputs,
                    AiModelUsage.EMPTY,
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private Mono<AiModelResponse> rerank(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var texts = new ArrayList<String>();
        for (var input : request.inputs()) {
            if (input instanceof AiModelContent.Text text) {
                texts.add(text.text());
            } else {
                return Mono.error(new IllegalArgumentException("OpenAI 重排协议仅支持文本输入"));
            }
        }
        var modelCode = modelCode(model);
        if (texts.size() < 2) {
            return Mono.just(new AiModelResponse(
                    null,
                    modelCode,
                    List.of(),
                    AiModelUsage.EMPTY,
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED));
        }
        var documents = List.copyOf(texts.subList(1, texts.size()));
        var topK = integerParam(request.params(), "topK", documents.size());
        return rerankClient
                .rerank(provider, account, modelCode, new RerankRequest(texts.getFirst(), documents, topK))
                .map(result -> new AiModelResponse(
                        null,
                        modelCode,
                        scoredOutputs(result),
                        AiModelUsage.EMPTY,
                        null,
                        null,
                        AiModelResponse.TaskStatus.SUCCEEDED));
    }

    private List<AiModelContent> scoredOutputs(RerankResult result) {
        var outputs = new ArrayList<AiModelContent>();
        for (var scored : result.results()) {
            var node = mapper.createObjectNode();
            node.put("index", scored.index());
            node.put("score", scored.score());
            outputs.add(new AiModelContent.Json(node));
        }
        return outputs;
    }

    private static int integerParam(Map<String, JsonNode> params, String name, int fallback) {
        var value = params == null ? null : params.get(name);
        return value != null && value.isIntegralNumber() ? value.asInt() : fallback;
    }

    private ObjectNode body(AiModel model, AiModelRequest request) {
        var body = mapper.createObjectNode();
        request.params().forEach(body::set);
        if (!body.has("model")) {
            body.put("model", modelCode(model));
        }
        return body;
    }

    private static String text(List<AiModelContent> inputs) {
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

    private static FileInput fileInput(List<AiModelContent> inputs) {
        for (var input : inputs) {
            if (input instanceof AiModelContent.Audio audio && audio.data() != null) {
                return new FileInput(
                        "audio." + (audio.format() == null ? "mp3" : audio.format()),
                        audio.mediaType() == null ? "audio/mpeg" : audio.mediaType(),
                        Base64.getDecoder().decode(audio.data()));
            }
            if (input instanceof AiModelContent.File file && file.data() != null) {
                return new FileInput(
                        file.filename() == null ? "audio.mp3" : file.filename(),
                        file.mediaType() == null ? "audio/mpeg" : file.mediaType(),
                        Base64.getDecoder().decode(file.data()));
            }
        }
        return null;
    }

    private static void stringParam(
            Map<String, JsonNode> params, String name, java.util.function.Consumer<String> consumer) {
        var value = params == null ? null : params.get(name);
        if (value != null && value.isTextual()) {
            consumer.accept(value.textValue());
        }
    }

    private static String modelCode(AiModel model) {
        return model == null || model.getSpec() == null ? "" : model.getSpec().getModelCode();
    }

    private static String audioMediaType(String format) {
        return "wav".equals(format) ? "audio/wav" : "pcm".equals(format) ? "audio/pcm" : "audio/mpeg";
    }

    private record FileInput(String filename, String mediaType, byte[] bytes) {}
}
