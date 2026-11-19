package cn.tolim.pudans.core.ai.provider.supplier.qianwen;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.model.AiModelAdapter;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import cn.tolim.pudans.core.ai.provider.AiProviderHttpSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class QianwenModelAdapter implements AiModelAdapter {

    private static final String MULTIMODAL_PATH = QianwenProtocol.MULTIMODAL_PATH;

    private static final String VIDEO_PATH = QianwenProtocol.VIDEO_PATH;

    private static final String THREE_D_PATH = QianwenProtocol.THREE_D_PATH;

    private static final String EMBEDDING_PATH = QianwenProtocol.EMBEDDING_PATH;

    private static final String RERANK_PATH = QianwenProtocol.RERANK_PATH;

    private static final String TEXT_EMBEDDING_PATH = QianwenProtocol.TEXT_EMBEDDING_PATH;

    private static final String SPEECH_PATH = QianwenProtocol.SPEECH_PATH;

    private static final String ASR_PATH = QianwenProtocol.ASR_PATH;

    private static final String MUSIC_PATH = QianwenProtocol.MUSIC_PATH;

    private static final String TASK_PATH = QianwenProtocol.TASK_PATH;

    private static final Set<AiProvider.Protocol> PROTOCOLS = Set.of(AiProvider.Protocol.QIANWEN);

    private static final Set<AiModel.Capability> CAPABILITIES = Set.of(
            AiModel.Capability.IMAGE_GENERATION,
            AiModel.Capability.IMAGE_EDITING,
            AiModel.Capability.VIDEO_GENERATION,
            AiModel.Capability.THREE_D_GENERATION,
            AiModel.Capability.EMBEDDING,
            AiModel.Capability.RERANK,
            AiModel.Capability.VISION,
            AiModel.Capability.AUDIO_UNDERSTANDING,
            AiModel.Capability.VIDEO_UNDERSTANDING,
            AiModel.Capability.SPEECH_SYNTHESIS,
            AiModel.Capability.AUDIO_GENERATION,
            AiModel.Capability.SPEECH_RECOGNITION,
            AiModel.Capability.MUSIC_GENERATION,
            AiModel.Capability.OCR);

    private final QianwenApiClient client;

    private final ObjectMapper mapper;

    public QianwenModelAdapter(QianwenApiClient client, ObjectMapper mapper) {
        this.client = client;
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
            case IMAGE_GENERATION,
                    IMAGE_EDITING,
                    VISION,
                    AUDIO_UNDERSTANDING,
                    VIDEO_UNDERSTANDING,
                    OCR -> multimodal(provider, account, model, request);
            case VIDEO_GENERATION -> video(provider, account, model, request);
            case THREE_D_GENERATION -> threeD(provider, account, model, request);
            case EMBEDDING -> embed(provider, account, model, request);
            case RERANK -> rerank(provider, account, model, request);
            case SPEECH_SYNTHESIS, AUDIO_GENERATION -> speech(provider, account, model, request);
            case SPEECH_RECOGNITION -> asr(provider, account, model, request);
            case MUSIC_GENERATION -> music(provider, account, model, request);
            default -> Mono.error(new UnsupportedOperationException(
                    "千问不支持模型能力：" + request.capability()));
        };
    }

    @Override
    public Mono<AiModelResponse> poll(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModel.Capability capability,
            String providerTaskId,
            @Nullable Map<String, ?> payload) {
        return client.get(provider, account, TASK_PATH + providerTaskId)
                .flatMap(root -> capability == AiModel.Capability.SPEECH_RECOGNITION
                        ? speechRecognitionResponse(root, model)
                        : Mono.just(taskResponse(root, model, capability)));
    }

    private Mono<AiModelResponse> multimodal(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = body(model, request);
        if (!body.has("input")) {
            var input = body.putObject("input");
            var messages = input.putArray("messages");
            var message = messages.addObject().put("role", "user");
            var content = message.putArray("content");
            var prompt = prompt(request.inputs());
            if (!prompt.isEmpty()) {
                content.addObject().put("text", prompt);
            }
            for (var item : request.inputs()) {
                if (item instanceof AiModelContent.Image image) {
                    content.addObject()
                            .put("image", imageReference(image.url(), image.data(), image.mediaType()));
                } else if (item instanceof AiModelContent.Video video) {
                    if (video.url() == null) {
                        throw new IllegalArgumentException("千问视频理解需要公网 URL");
                    }
                    content.addObject().put("video", video.url());
                } else if (item instanceof AiModelContent.Audio audio) {
                    content.addObject()
                            .put("audio", mediaReference(
                                    audio.url(), audio.data(), audio.mediaType(), "audio/mpeg"));
                }
            }
        }
        return client.post(provider, account, MULTIMODAL_PATH, body, false).map(root -> {
            var outputs = new ArrayList<AiModelContent>();
            var choices = root.path("output").path("choices");
            for (var choice : choices) {
                var content = choice.path("message").path("content");
                if (content.isTextual()) {
                    outputs.add(new AiModelContent.Text(content.asText()));
                    continue;
                }
                for (var part : content) {
                    if (part.hasNonNull("image")) {
                        outputs.add(new AiModelContent.Image(
                                part.get("image").asText(), null, null, null, null));
                    } else if (part.hasNonNull("text")) {
                        outputs.add(new AiModelContent.Text(part.get("text").asText()));
                    }
                }
            }
            for (var result : root.path("output").path("results")) {
                if (result.hasNonNull("url")) {
                    outputs.add(new AiModelContent.Image(
                            result.get("url").asText(), null, null, null, null));
                }
            }
            var usage = root.has("usage") ? root.get("usage") : mapper.createObjectNode();
            var imageCapability = request.capability() == AiModel.Capability.IMAGE_GENERATION
                    || request.capability() == AiModel.Capability.IMAGE_EDITING;
            Integer imageCount = imageCapability
                    ? firstInteger(usage, "output_image_count", "image_count")
                    : null;
            if (imageCapability && imageCount == null) {
                imageCount = outputs.size();
            }
            return new AiModelResponse(
                    text(root, "request_id"),
                    modelCode(model),
                    outputs,
                    new AiModelUsage(
                            AiProviderHttpSupport.integer(usage, "input_tokens"),
                            AiProviderHttpSupport.integer(usage, "output_tokens"),
                            null, null,
                            imageCount,
                            null, null, null, null, Map.of()),
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private Mono<AiModelResponse> video(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = body(model, request);
        if (!body.has("input")) {
            var input = body.putObject("input");
            var prompt = prompt(request.inputs());
            if (!prompt.isEmpty()) {
                input.put("prompt", prompt);
            }
            var urls = imageUrls(request.inputs());
            if (urls.size() == 1) {
                input.put("img_url", urls.getFirst());
            } else if (urls.size() > 1) {
                var media = input.putArray("media");
                for (var url : urls) {
                    media.addObject().put("type", "reference_image").put("url", url);
                }
            }
        }
        return client.post(provider, account, VIDEO_PATH, body, true)
                .map(root -> created(root, model));
    }

    private Mono<AiModelResponse> threeD(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = body(model, request);
        if (!body.has("input")) {
            var input = body.putObject("input");
            var prompt = prompt(request.inputs());
            if (!prompt.isEmpty()) {
                input.put("prompt", prompt);
            }
            var images = request.inputs().stream()
                    .filter(AiModelContent.Image.class::isInstance)
                    .map(AiModelContent.Image.class::cast)
                    .toList();
            if (images.size() > 4) {
                return Mono.error(new IllegalArgumentException("千问 3D 生成最多支持 4 张图片"));
            }
            if (images.size() == 1) {
                if (images.getFirst().url() == null) {
                    return Mono.error(new IllegalArgumentException("千问 3D 生成需要图片公网 URL"));
                }
                input.put("image", images.getFirst().url());
            } else if (images.size() > 1) {
                var items = input.putArray("images");
                for (var image : images) {
                    if (image.url() == null) {
                        return Mono.error(new IllegalArgumentException("千问 3D 生成需要图片公网 URL"));
                    }
                    items.addObject()
                            .put("type", imageType(image.mediaType()))
                            .put("file_token", image.url());
                }
                while (items.size() < 4) {
                    items.addObject();
                }
            }
        }
        return client.post(provider, account, THREE_D_PATH, body, true)
                .map(root -> created(root, model));
    }

    private Mono<AiModelResponse> embed(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var modelCode = modelCode(model);
        var textOnly = request.inputs().stream().allMatch(AiModelContent.Text.class::isInstance);
        if (textOnly && modelCode.startsWith("text-embedding")) {
            return textEmbedding(provider, account, modelCode, request);
        }
        return multimodalEmbedding(provider, account, modelCode, request);
    }

    private Mono<AiModelResponse> textEmbedding(
            AiProvider provider,
            AiProviderAccount account,
            String modelCode,
            AiModelRequest request) {
        var body = mapper.createObjectNode();
        body.put("model", modelCode);
        var input = body.putObject("input");
        var texts = input.putArray("texts");
        for (var content : request.inputs()) {
            texts.add(((AiModelContent.Text) content).text());
        }
        var parameters = body.putObject("parameters");
        request.params().forEach(parameters::set);
        return client.post(provider, account, TEXT_EMBEDDING_PATH, body, false).map(root -> {
            var vectors = new TreeMap<Integer, float[]>();
            var embeddings = root.path("output").path("embeddings");
            for (var position = 0; position < embeddings.size(); position++) {
                var item = embeddings.get(position);
                vectors.put(optionalInteger(item, "text_index", position), vector(item.get("embedding")));
            }
            return embeddingResponse(
                    root,
                    modelCode,
                    vectors,
                    AiProviderHttpSupport.integer(root.path("usage"), "total_tokens"));
        });
    }

    private Mono<AiModelResponse> multimodalEmbedding(
            AiProvider provider,
            AiProviderAccount account,
            String modelCode,
            AiModelRequest request) {
        var body = mapper.createObjectNode();
        body.put("model", modelCode);
        var input = body.putObject("input");
        var contents = input.putArray("contents");
        for (var content : request.inputs()) {
            contents.add(contentItem(content));
        }
        var parameters = body.putObject("parameters");
        request.params().forEach(parameters::set);
        return client.post(provider, account, EMBEDDING_PATH, body, false).map(root -> {
            var vectors = new TreeMap<Integer, float[]>();
            var embeddings = root.path("output").path("embeddings");
            for (var position = 0; position < embeddings.size(); position++) {
                var item = embeddings.get(position);
                vectors.put(optionalInteger(item, "index", position), vector(item.get("embedding")));
            }
            return embeddingResponse(
                    root,
                    modelCode,
                    vectors,
                    AiProviderHttpSupport.integer(root.path("usage"), "input_tokens"));
        });
    }

    private AiModelResponse embeddingResponse(
            JsonNode root,
            String modelCode,
            TreeMap<Integer, float[]> vectors,
            @Nullable Integer inputTokens) {
        var outputs = new ArrayList<AiModelContent>();
        for (var values : vectors.values()) {
            outputs.add(new AiModelContent.Vector(modelCode, values.length, values));
        }
        return new AiModelResponse(
                text(root, "request_id"),
                modelCode,
                outputs,
                new AiModelUsage(
                        inputTokens, null, null, null, null, null, null, null, null, Map.of()),
                null,
                null,
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private Mono<AiModelResponse> speech(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var text = prompt(request.inputs());
        if (text.isEmpty()) {
            return Mono.error(new IllegalArgumentException("千问语音合成需要文本内容"));
        }
        var modelCode = modelCode(model);
        var body = mapper.createObjectNode();
        body.put("model", modelCode);
        var input = body.putObject("input");
        input.put("text_prompt", text);
        var source = request.params().get("input");
        if (source != null && source.isObject()) {
            source.fields().forEachRemaining(entry -> input.set(entry.getKey(), entry.getValue()));
        } else {
            request.params().forEach((name, value) -> {
                if (!"parameters".equals(name) && !"input".equals(name)) {
                    input.set(name, value);
                }
            });
        }
        var parameters = body.putObject("parameters");
        var nested = request.params().get("parameters");
        if (nested != null && nested.isObject()) {
            nested.fields().forEachRemaining(entry -> parameters.set(entry.getKey(), entry.getValue()));
        }
        return client.post(provider, account, SPEECH_PATH, body, false).map(root -> {
            var audio = root.path("output").path("audio");
            var format = text(input, "format") == null ? "wav" : text(input, "format");
            var duration = decimal(audio, "duration");
            var outputs = new ArrayList<AiModelContent>();
            var url = text(audio, "url");
            var data = text(audio, "data");
            if (url != null || data != null) {
                outputs.add(new AiModelContent.Audio(
                        url,
                        data,
                        audioMediaType(format),
                        duration == null ? null : (int) Math.ceil(duration),
                        format,
                        null));
            }
            return new AiModelResponse(
                    text(root, "request_id"),
                    modelCode,
                    outputs,
                    new AiModelUsage(
                            null, null, null, null, null, null,
                            duration == null ? null : (int) Math.ceil(duration),
                            text.length(), null, Map.of()),
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private Mono<AiModelResponse> asr(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var url = audioUrl(request.inputs());
        if (url == null) {
            return Mono.error(new IllegalArgumentException("千问语音识别需要音频公网 URL"));
        }
        var body = mapper.createObjectNode();
        body.put("model", modelCode(model));
        var input = body.putObject("input");
        input.putArray("file_urls").add(url);
        var parameters = body.putObject("parameters");
        var nested = request.params().get("parameters");
        if (nested != null && nested.isObject()) {
            nested.fields().forEachRemaining(entry -> parameters.set(entry.getKey(), entry.getValue()));
        }
        request.params().forEach((name, value) -> {
            if (!"parameters".equals(name) && !"input".equals(name) && !"fileUrls".equals(name)) {
                parameters.set(name, value);
            }
        });
        return client.post(provider, account, ASR_PATH, body, true)
                .map(root -> created(root, model));
    }

    private Mono<AiModelResponse> music(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = mapper.createObjectNode();
        body.put("model", modelCode(model));
        var input = body.putObject("input");
        var prompt = prompt(request.inputs());
        if (!prompt.isEmpty()) {
            input.put("prompt", prompt);
        }
        var source = request.params().get("input");
        if (source != null && source.isObject()) {
            source.fields().forEachRemaining(entry -> input.set(entry.getKey(), entry.getValue()));
        } else {
            request.params().forEach((name, value) -> {
                if (!"parameters".equals(name) && !"input".equals(name)) {
                    input.set(name, value);
                }
            });
        }
        return client.post(provider, account, MUSIC_PATH, body, false)
                .map(root -> musicResponse(root, model, input));
    }

    private AiModelResponse musicResponse(JsonNode root, AiModel model, JsonNode input) {
        var output = root.has("output") ? root.get("output") : root;
        var audio = output.path("audio");
        var url = text(audio, "url");
        var data = text(audio, "data");
        var format = text(input, "format") == null ? "mp3" : text(input, "format");
        var duration = AiProviderHttpSupport.integer(root.path("usage"), "duration");
        var outputs = new ArrayList<AiModelContent>();
        if (url != null || (data != null && !data.isEmpty())) {
            outputs.add(new AiModelContent.Audio(
                    url,
                    data == null || data.isEmpty() ? null : data,
                    audioMediaType(format),
                    duration,
                    format,
                    null));
        }
        return new AiModelResponse(
                text(root, "request_id"),
                modelCode(model),
                outputs,
                new AiModelUsage(null, null, null, null, null, null, duration, null, null, Map.of()),
                null,
                null,
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private Mono<AiModelResponse> speechRecognitionResponse(JsonNode root, AiModel model) {
        var output = root.has("output") ? root.get("output") : root;
        if (!"SUCCEEDED".equals(text(output, "task_status"))) {
            return Mono.just(taskResponse(root, model, AiModel.Capability.SPEECH_RECOGNITION));
        }
        var transcriptionUrl = transcriptionUrl(output);
        if (transcriptionUrl == null) {
            return Mono.just(taskResponse(root, model, AiModel.Capability.SPEECH_RECOGNITION));
        }
        return client.getExternal(transcriptionUrl).map(document -> {
            var text = new StringBuilder();
            var audioSeconds = AiProviderHttpSupport.integer(root.path("usage"), "duration");
            for (var transcript : document.path("transcripts")) {
                var line = text(transcript, "text");
                if (line != null) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append(line);
                }
                var duration = AiProviderHttpSupport.integer(
                        transcript, "content_duration_in_milliseconds");
                if (audioSeconds == null && duration != null) {
                    audioSeconds = (int) Math.ceil(duration / 1000.0);
                }
            }
            return new AiModelResponse(
                    text(output, "task_id"),
                    modelCode(model),
                    text.isEmpty() ? List.of() : List.of(new AiModelContent.Text(text.toString())),
                    new AiModelUsage(
                            null, null, null, null, null, null, audioSeconds, null, null, Map.of()),
                    null,
                    text(output, "task_id"),
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private Mono<AiModelResponse> rerank(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var modelCode = modelCode(model);
        if (request.inputs().isEmpty()) {
            return Mono.just(new AiModelResponse(
                    null,
                    modelCode,
                    List.of(),
                    AiModelUsage.EMPTY,
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED));
        }
        var body = mapper.createObjectNode();
        body.put("model", modelCode);
        var input = body.putObject("input");
        input.set("query", contentItem(request.inputs().getFirst()));
        var documents = input.putArray("documents");
        for (var content : request.inputs().subList(1, request.inputs().size())) {
            documents.add(contentItem(content));
        }
        var parameters = body.putObject("parameters");
        var topK = integerParam(request.params(), "topK", documents.size());
        if (topK > 0) {
            parameters.put("top_n", topK);
        }
        request.params().forEach((name, value) -> {
            if (!"topK".equals(name)) {
                parameters.set(name, value);
            }
        });
        return client.post(provider, account, RERANK_PATH, body, false).map(root -> {
            var results = root.path("output").path("results");
            if (!results.isArray()) {
                results = root.path("results");
            }
            var outputs = new ArrayList<AiModelContent>();
            for (var position = 0; position < results.size(); position++) {
                var item = results.get(position);
                var index = optionalInteger(item, "index", position);
                var score = decimal(item, "relevance_score");
                if (score == null) {
                    score = decimal(item, "score");
                }
                if (score == null) {
                    throw new IllegalArgumentException("千问重排响应缺少有效分数");
                }
                var node = mapper.createObjectNode();
                node.put("index", index);
                node.put("score", score);
                outputs.add(new AiModelContent.Json(node));
            }
            var usage = root.path("usage");
            return new AiModelResponse(
                    text(root, "request_id"),
                    modelCode,
                    outputs,
                    new AiModelUsage(
                            AiProviderHttpSupport.integer(usage, "total_tokens"),
                            null, null, null, null, null, null, null, null, Map.of()),
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private ObjectNode contentItem(AiModelContent content) {
        var item = mapper.createObjectNode();
        if (content instanceof AiModelContent.Text text) {
            item.put("text", text.text());
        } else if (content instanceof AiModelContent.Image image) {
            item.put("image", imageReference(image.url(), image.data(), image.mediaType()));
        } else if (content instanceof AiModelContent.Video video) {
            if (video.url() == null) {
                throw new IllegalArgumentException("千问视频向量输入需要公网 URL");
            }
            item.put("video", video.url());
        } else {
            throw new IllegalArgumentException("千问不支持该向量输入类型：" + content.getClass().getSimpleName());
        }
        return item;
    }

    private static String imageReference(
            @Nullable String url, @Nullable String data, @Nullable String mediaType) {
        return mediaReference(url, data, mediaType, "image/png");
    }

    private static String mediaReference(
            @Nullable String url,
            @Nullable String data,
            @Nullable String mediaType,
            String fallbackMediaType) {
        if (url != null) {
            return url;
        }
        if (data == null) {
            throw new IllegalArgumentException("千问媒体输入需要 URL 或 Base64 数据");
        }
        return "data:" + (mediaType == null ? fallbackMediaType : mediaType) + ";base64," + data;
    }

    private static float[] vector(JsonNode embedding) {
        if (embedding == null || !embedding.isArray()) {
            throw new IllegalArgumentException("千问向量响应缺少向量数据");
        }
        var values = new float[embedding.size()];
        for (var position = 0; position < embedding.size(); position++) {
            var value = embedding.get(position);
            if (value == null || !value.isNumber()) {
                throw new IllegalArgumentException("千问向量响应包含非数值元素");
            }
            values[position] = value.floatValue();
        }
        return values;
    }

    private static int optionalInteger(JsonNode node, String field, int fallback) {
        var value = AiProviderHttpSupport.integer(node, field);
        return value == null ? fallback : value;
    }

    private static @Nullable Double decimal(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.doubleValue() : null;
    }

    private static int integerParam(Map<String, JsonNode> params, String name, int fallback) {
        var value = params == null ? null : params.get(name);
        return value != null && value.isIntegralNumber() ? value.asInt() : fallback;
    }

    private AiModelResponse created(JsonNode root, AiModel model) {
        var output = root.has("output") ? root.get("output") : root;
        var taskId = text(output, "task_id");
        if (taskId == null) {
            throw new IllegalArgumentException("千问任务缺少 task_id");
        }
        return new AiModelResponse(
                text(root, "request_id"),
                modelCode(model),
                List.of(),
                null,
                null,
                taskId,
                AiModelResponse.TaskStatus.PENDING);
    }

    private AiModelResponse taskResponse(
            JsonNode root, AiModel model, AiModel.Capability capability) {
        var output = root.has("output") ? root.get("output") : root;
        var status = text(output, "task_status");
        var state = switch (status == null ? "" : status) {
            case "SUCCEEDED" -> AiModelResponse.TaskStatus.SUCCEEDED;
            case "FAILED", "CANCELED", "UNKNOWN" -> AiModelResponse.TaskStatus.FAILED;
            default -> AiModelResponse.TaskStatus.RUNNING;
        };
        var outputs = new ArrayList<AiModelContent>();
        if (state == AiModelResponse.TaskStatus.SUCCEEDED) {
            collectOutputs(outputs, output, capability);
        }
        var message = text(output, "message");
        return new AiModelResponse(
                text(output, "task_id"),
                modelCode(model),
                outputs,
                usage(root, output, outputs, capability),
                message,
                text(output, "task_id"),
                state);
    }

    private void collectOutputs(
            List<AiModelContent> outputs, JsonNode output, AiModel.Capability capability) {
        switch (capability) {
            case VIDEO_GENERATION -> {
                if (output.hasNonNull("video_url")) {
                    outputs.add(new AiModelContent.Video(
                            output.get("video_url").asText(), null, null, null, null));
                }
                for (var result : output.path("results")) {
                    var url = firstText(result, "video_url", "url");
                    if (url != null) {
                        outputs.add(new AiModelContent.Video(url, null, null, null, null));
                    }
                }
            }
            case THREE_D_GENERATION -> {
                for (var result : output.path("results")) {
                    var url = firstText(result, "pbr_model_url", "model_url", "base_model_url");
                    if (url != null) {
                        outputs.add(new AiModelContent.ThreeD(
                                url, null, threeDMediaType(url), null));
                    }
                }
            }
            case IMAGE_GENERATION, IMAGE_EDITING -> {
                for (var result : output.path("results")) {
                    var url = firstText(result, "url", "image_url");
                    if (url != null) {
                        outputs.add(new AiModelContent.Image(url, null, null, null, null));
                    }
                }
            }
            default -> {}
        }
    }

    private AiModelUsage usage(
            JsonNode root,
            JsonNode output,
            List<AiModelContent> outputs,
            AiModel.Capability capability) {
        var usage = output.has("usage") ? output.get("usage") : root.path("usage");
        var imageCount = firstInteger(usage, "output_image_count", "image_count");
        var seconds = firstInteger(usage, "output_video_duration", "duration", "duration_seconds");
        var videoSeconds = capability == AiModel.Capability.VIDEO_GENERATION ? seconds : null;
        var audioSeconds = capability == AiModel.Capability.SPEECH_RECOGNITION
                        || capability == AiModel.Capability.AUDIO_GENERATION
                        || capability == AiModel.Capability.MUSIC_GENERATION
                        || capability == AiModel.Capability.SPEECH_SYNTHESIS
                ? seconds
                : null;
        var taskCount = capability == AiModel.Capability.THREE_D_GENERATION ? 1 : null;
        if (imageCount == null
                && (capability == AiModel.Capability.IMAGE_GENERATION
                        || capability == AiModel.Capability.IMAGE_EDITING)) {
            imageCount = outputs.size();
        }
        return new AiModelUsage(
                AiProviderHttpSupport.integer(usage, "input_tokens"),
                AiProviderHttpSupport.integer(usage, "output_tokens"),
                null, null,
                imageCount,
                videoSeconds,
                audioSeconds,
                null,
                taskCount,
                Map.of());
    }

    private ObjectNode body(AiModel model, AiModelRequest request) {
        var body = mapper.createObjectNode();
        body.put("model", modelCode(model));
        var params = request.params();
        var input = params.get("input");
        if (input != null && input.isObject()) {
            body.set("input", input);
        }
        var parameters = body.putObject("parameters");
        var source = params.get("parameters");
        if (source != null && source.isObject()) {
            source.fields().forEachRemaining(entry -> parameters.set(entry.getKey(), entry.getValue()));
        }
        params.forEach((name, value) -> {
            if (!"input".equals(name) && !"parameters".equals(name) && !"model".equals(name)) {
                parameters.set(name, value);
            }
        });
        return body;
    }

    private static @Nullable Integer firstInteger(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = AiProviderHttpSupport.integer(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable String firstText(JsonNode node, String... fields) {
        for (var field : fields) {
            var value = AiProviderHttpSupport.text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static List<String> imageUrls(List<AiModelContent> inputs) {
        var urls = new ArrayList<String>();
        for (var image : images(inputs)) {
            urls.add(image.url());
        }
        return urls;
    }

    private static @Nullable String audioUrl(List<AiModelContent> inputs) {
        for (var input : inputs) {
            if (input instanceof AiModelContent.Audio audio && audio.url() != null) {
                return audio.url();
            }
            if (input instanceof AiModelContent.File file && file.url() != null) {
                return file.url();
            }
        }
        return null;
    }

    private static @Nullable String transcriptionUrl(JsonNode output) {
        for (var result : output.path("results")) {
            var url = text(result, "transcription_url");
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static List<ImageInput> images(List<AiModelContent> inputs) {
        var images = new ArrayList<ImageInput>();
        for (var input : inputs) {
            if (input instanceof AiModelContent.Image image) {
                var url = image.url() != null ? image.url() : image.data();
                if (url != null) {
                    images.add(new ImageInput(url, image.mediaType()));
                }
            }
        }
        return images;
    }

    private static String prompt(List<AiModelContent> inputs) {
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

    private static String imageType(@Nullable String mediaType) {
        return mediaType != null && mediaType.contains("jpeg") ? "jpeg" : "png";
    }

    private static String threeDMediaType(String url) {
        var path = url.indexOf('?') < 0 ? url : url.substring(0, url.indexOf('?'));
        return path.endsWith(".fbx") ? "application/octet-stream" : "model/gltf-binary";
    }

    private static String audioMediaType(String format) {
        return switch (format) {
            case "wav" -> "audio/wav";
            case "mp3" -> "audio/mpeg";
            case "pcm" -> "audio/pcm";
            case "opus" -> "audio/opus";
            default -> "audio/" + format;
        };
    }

    private static @Nullable String text(JsonNode node, String field) {
        return AiProviderHttpSupport.text(node, field);
    }

    private static String modelCode(AiModel model) {
        return model == null || model.getSpec() == null ? "" : model.getSpec().getModelCode();
    }

    private record ImageInput(String url, @Nullable String mediaType) {}
}
