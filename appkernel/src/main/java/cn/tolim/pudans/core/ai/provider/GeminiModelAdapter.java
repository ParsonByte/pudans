package cn.tolim.pudans.core.ai.provider;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiProviderAccount;
import cn.tolim.pudans.core.ai.model.AiModelAdapter;
import cn.tolim.pudans.core.ai.model.AiModelContent;
import cn.tolim.pudans.core.ai.model.AiModelRequest;
import cn.tolim.pudans.core.ai.model.AiModelResponse;
import cn.tolim.pudans.core.ai.model.AiModelUsage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class GeminiModelAdapter implements AiModelAdapter {

    private static final String GENERATE_SUFFIX = ":generateContent";

    private static final String VIDEO_SUFFIX = ":predictLongRunning";

    private static final Set<AiProvider.Protocol> PROTOCOLS = Set.of(AiProvider.Protocol.GEMINI);

    private static final Set<AiModel.Capability> CAPABILITIES = Set.of(
            AiModel.Capability.IMAGE_GENERATION,
            AiModel.Capability.VIDEO_GENERATION);

    private final GeminiMediaClient client;

    private final ObjectMapper mapper;

    public GeminiModelAdapter(GeminiMediaClient client, ObjectMapper mapper) {
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
            case IMAGE_GENERATION -> image(provider, account, model, request);
            case VIDEO_GENERATION -> video(provider, account, model, request);
            default -> Mono.error(new UnsupportedOperationException(
                    "Gemini 不支持模型能力：" + request.capability()));
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
        return client.get(provider, account, "/v1beta/" + providerTaskId).map(this::videoResponse);
    }

    private Mono<AiModelResponse> image(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = imageBody(model, request);
        var path = "/v1beta/models/" + modelCode(model) + GENERATE_SUFFIX;
        return client.post(provider, account, path, body).map(root -> {
            var outputs = new ArrayList<AiModelContent>();
            var candidates = root.get("candidates");
            if (candidates != null && candidates.isArray()) {
                for (var candidate : candidates) {
                    addImageParts(outputs, candidate.get("content"));
                }
            }
            var predictions = root.get("predictions");
            if (predictions != null && predictions.isArray()) {
                for (var prediction : predictions) {
                    if (prediction.hasNonNull("bytesBase64Encoded")) {
                        outputs.add(new AiModelContent.Image(
                                null,
                                prediction.get("bytesBase64Encoded").asText(),
                                text(prediction, "mimeType"),
                                null,
                                null));
                    }
                }
            }
            return new AiModelResponse(
                    null,
                    modelCode(model),
                    outputs,
                    new AiModelUsage(null, null, null, null, outputs.size(), null, null, null, null, Map.of()),
                    null,
                    null,
                    AiModelResponse.TaskStatus.SUCCEEDED);
        });
    }

    private Mono<AiModelResponse> video(
            AiProvider provider, AiProviderAccount account, AiModel model, AiModelRequest request) {
        var body = videoBody(model, request);
        var path = "/v1beta/models/" + modelCode(model) + VIDEO_SUFFIX;
        return client.post(provider, account, path, body).map(root -> {
            var name = text(root, "name");
            if (name == null) {
                throw new IllegalArgumentException("Gemini 视频任务缺少 operation name");
            }
            return new AiModelResponse(
                    null, modelCode(model), List.of(), null, null, name,
                    AiModelResponse.TaskStatus.PENDING);
        });
    }

    private AiModelResponse videoResponse(JsonNode root) {
        var done = root.has("done") && root.get("done").isBoolean() && root.get("done").asBoolean();
        if (!done) {
            return new AiModelResponse(
                    null, null, List.of(), null, null, text(root, "name"),
                    AiModelResponse.TaskStatus.RUNNING);
        }
        if (root.has("error")) {
            return new AiModelResponse(
                    null, null, List.of(), null, text(root.get("error"), "message"),
                    text(root, "name"), AiModelResponse.TaskStatus.FAILED);
        }
        var outputs = new ArrayList<AiModelContent>();
        addVideoUrls(outputs, root.get("response"));
        return new AiModelResponse(
                text(root, "name"),
                null,
                outputs,
                new AiModelUsage(null, null, null, null, null, null, null, null, null, Map.of()),
                null,
                text(root, "name"),
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private ObjectNode imageBody(AiModel model, AiModelRequest request) {
        var body = mapper.createObjectNode();
        request.params().forEach(body::set);
        if (body.has("instances") || body.has("predictions")) {
            return body;
        }
        var contents = body.has("contents") ? (ArrayNode) body.get("contents") : body.putArray("contents");
        var content = contents.addObject();
        content.put("role", "user");
        var parts = content.putArray("parts");
        parts.addObject().put("text", text(request.inputs()));
        var generation = body.has("generationConfig") && body.get("generationConfig").isObject()
                ? (ObjectNode) body.get("generationConfig")
                : body.putObject("generationConfig");
        if (!generation.has("responseModalities")) {
            generation.putArray("responseModalities").add("TEXT").add("IMAGE");
        }
        return body;
    }

    private ObjectNode videoBody(AiModel model, AiModelRequest request) {
        var body = mapper.createObjectNode();
        request.params().forEach(body::set);
        if (body.has("instances")) {
            return body;
        }
        var instances = body.putArray("instances");
        instances.addObject().put("prompt", text(request.inputs()));
        if (!body.has("parameters")) {
            body.putObject("parameters");
        }
        return body;
    }

    private static void addImageParts(List<AiModelContent> outputs, JsonNode content) {
        var parts = content == null ? null : content.get("parts");
        if (parts == null || !parts.isArray()) {
            return;
        }
        for (var part : parts) {
            var inline = part.get("inlineData") != null ? part.get("inlineData") : part.get("inline_data");
            if (inline != null && inline.hasNonNull("data")) {
                outputs.add(new AiModelContent.Image(
                        null,
                        inline.get("data").asText(),
                        text(inline, "mimeType") != null
                                ? text(inline, "mimeType")
                                : text(inline, "mime_type"),
                        null,
                        null));
            }
        }
    }

    private static void addVideoUrls(List<AiModelContent> outputs, JsonNode response) {
        if (response == null) {
            return;
        }
        var generated = response.path("generateVideoResponse").path("generatedSamples");
        if (generated.isArray()) {
            for (var sample : generated) {
                var uri = sample.path("video").path("uri");
                if (uri.isTextual()) {
                    outputs.add(new AiModelContent.Video(uri.asText(), null, null, null, null));
                }
            }
        }
        var predictions = response.get("predictions");
        if (predictions != null && predictions.isArray()) {
            for (var prediction : predictions) {
                if (prediction.hasNonNull("videoUri")) {
                    outputs.add(new AiModelContent.Video(
                            prediction.get("videoUri").asText(), null, null, null, null));
                }
            }
        }
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

    private static @Nullable String text(JsonNode node, String field) {
        return AiProviderHttpSupport.text(node, field);
    }

    private static String modelCode(AiModel model) {
        return model == null || model.getSpec() == null ? "" : model.getSpec().getModelCode();
    }
}
