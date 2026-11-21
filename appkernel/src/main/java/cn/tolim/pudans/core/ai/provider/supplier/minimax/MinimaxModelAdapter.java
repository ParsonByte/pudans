package cn.tolim.pudans.core.ai.provider.supplier.minimax;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class MinimaxModelAdapter implements AiModelAdapter {

    private static final String IMAGE_PATH = "/v1/images/generations";

    private static final String IMAGE_EDIT_PATH = "/v1/images/edits";

    private static final String VIDEO_V2_PATH = "/v2/video_generation";

    private static final String VIDEO_V2_QUERY = "/v2/query/video_generation/";

    private static final String SPEECH_PATH = "/v1/t2a_v2";

    private static final String ASR_PATH = "/v1/speech_to_text";

    private static final String MUSIC_PATH = "/v1/music_generation";

    private static final Set<AiModel.Capability> CAPABILITIES = Set.of(
            AiModel.Capability.IMAGE_GENERATION,
            AiModel.Capability.IMAGE_EDITING,
            AiModel.Capability.VIDEO_GENERATION,
            AiModel.Capability.SPEECH_RECOGNITION,
            AiModel.Capability.SPEECH_SYNTHESIS,
            AiModel.Capability.AUDIO_GENERATION,
            AiModel.Capability.MUSIC_GENERATION);

    private final MinimaxApiClient client;

    private final ObjectMapper mapper;

    public MinimaxModelAdapter(MinimaxApiClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    @Override
    public Set<AiProvider.Protocol> protocols() {
        return MinimaxProtocol.PROTOCOLS;
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
            case IMAGE_GENERATION -> image(provider, account, model, request, IMAGE_PATH);
            case IMAGE_EDITING -> image(provider, account, model, request, IMAGE_EDIT_PATH);
            case VIDEO_GENERATION -> video(provider, account, model, request);
            case SPEECH_SYNTHESIS, AUDIO_GENERATION -> speech(provider, account, model, request);
            case SPEECH_RECOGNITION -> asr(provider, account, model, request);
            case MUSIC_GENERATION -> music(provider, account, model, request);
            default -> Mono.error(new UnsupportedOperationException(
                    "MiniMax 不支持模型能力：" + request.capability()));
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
        return client.get(provider, account, VIDEO_V2_QUERY + providerTaskId, Map.of())
                .map(this::videoResponse);
    }

    private Mono<AiModelResponse> image(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModelRequest request,
            String path) {
        var body = body(model, request);
        body.put("prompt", prompt(request.inputs()));
        body.put("stream", false);
        if (IMAGE_EDIT_PATH.equals(path)) {
            var images = body.putArray("images");
            for (var input : request.inputs()) {
                if (input instanceof AiModelContent.Image image) {
                    images.addObject().put("image_url", imageUrl(image));
                }
            }
            if (images.isEmpty()) {
                return Mono.error(new IllegalArgumentException("MiniMax 图片编辑需要参考图片"));
            }
        }
        return client.post(provider, account, path, body).map(this::imageResponse);
    }

    private Mono<AiModelResponse> video(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModelRequest request) {
        var body = body(model, request);
        if (!body.has("content")) {
            var content = body.putArray("content");
            var text = content.addObject();
            text.put("type", "text");
            text.put("text", prompt(request.inputs()));
            var images = 0;
            for (var input : request.inputs()) {
                if (input instanceof AiModelContent.Image image) {
                    var item = content.addObject();
                    item.put("type", "image_url");
                    var imageUrl = item.putObject("image_url");
                    imageUrl.put("url", imageUrl(image));
                    item.put("role", images == 0 ? "first_frame" : "reference_image");
                    images++;
                }
            }
        }
        return client.post(provider, account, VIDEO_V2_PATH, body).map(this::videoCreateResponse);
    }

    private Mono<AiModelResponse> speech(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModelRequest request) {
        var body = body(model, request);
        body.put("text", prompt(request.inputs()));
        body.put("stream", false);
        return client.post(provider, account, SPEECH_PATH, body).map(this::speechResponse);
    }

    private Mono<AiModelResponse> asr(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModelRequest request) {
        var audio = audioInput(request.inputs());
        if (audio == null) {
            return Mono.error(new IllegalArgumentException("语音识别需要音频字节内容"));
        }
        var fields = Map.of("model", modelCode(model));
        var language = request.params().get("language");
        var headers = language != null && language.isTextual()
                ? Map.of("language", language.asText())
                : Map.<String, String>of();
        return client
                .upload(
                        provider,
                        account,
                        ASR_PATH,
                        fields,
                        headers,
                        audio.filename(),
                        audio.mediaType(),
                        audio.bytes())
                .map(this::asrResponse);
    }

    private Mono<AiModelResponse> music(
            AiProvider provider,
            AiProviderAccount account,
            AiModel model,
            AiModelRequest request) {
        var body = body(model, request);
        if (!body.has("prompt")) {
            body.put("prompt", prompt(request.inputs()));
        }
        body.put("stream", false);
        return client.post(provider, account, MUSIC_PATH, body).map(this::musicResponse);
    }

    private ObjectNode body(AiModel model, AiModelRequest request) {
        var body = mapper.createObjectNode();
        request.params().forEach(body::set);
        if (!body.has("model")) {
            body.put("model", modelCode(model));
        }
        return body;
    }

    private AiModelResponse imageResponse(JsonNode root) {
        var outputs = new ArrayList<AiModelContent>();
        var data = root.get("data");
        if (data != null && data.isArray()) {
            for (var item : data) {
                if (item.hasNonNull("url")) {
                    outputs.add(new AiModelContent.Image(
                            item.get("url").asText(), null, null, null, null));
                } else if (item.hasNonNull("b64_json")) {
                    outputs.add(new AiModelContent.Image(
                            null, item.get("b64_json").asText(), mediaType(root), null, null));
                }
            }
        }
        var usage = root.get("usage");
        Integer count = usage == null ? null : AiProviderHttpSupport.integer(usage, "generated_images");
        return new AiModelResponse(
                text(root, "request_id"),
                null,
                outputs,
                new AiModelUsage(
                        usage == null ? null : AiProviderHttpSupport.integer(usage, "input_tokens"),
                        usage == null ? null : AiProviderHttpSupport.integer(usage, "output_tokens"),
                        null,
                        null,
                        count == null ? outputs.size() : count,
                        null,
                        null,
                        null,
                        null,
                        Map.of()),
                null,
                null,
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private AiModelResponse videoCreateResponse(JsonNode root) {
        var taskId = text(root, "task_id");
        if (taskId == null) {
            throw new IllegalArgumentException("MiniMax 视频任务缺少 task_id");
        }
        return new AiModelResponse(
                null, null, List.of(), null, null, taskId, AiModelResponse.TaskStatus.PENDING);
    }

    private AiModelResponse videoResponse(JsonNode root) {
        var task = root.has("task") ? root.get("task") : root;
        var status = text(task, "status");
        var state = switch (status == null ? "" : status) {
            case "succeeded" -> AiModelResponse.TaskStatus.SUCCEEDED;
            case "failed", "cancelled" -> AiModelResponse.TaskStatus.FAILED;
            default -> AiModelResponse.TaskStatus.RUNNING;
        };
        var outputs = new ArrayList<AiModelContent>();
        var content = task.get("content");
        if (content != null && content.hasNonNull("url")) {
            var duration = AiProviderHttpSupport.integer(task, "duration");
            outputs.add(new AiModelContent.Video(
                    content.get("url").asText(), null, null, duration, null));
        }
        var usageNode = task.get("usage");
        var seconds = usageNode == null
                ? AiProviderHttpSupport.integer(task, "duration")
                : AiProviderHttpSupport.integer(usageNode, "output_seconds");
        return new AiModelResponse(
                text(task, "id"),
                text(task, "model"),
                outputs,
                new AiModelUsage(
                        usageNode == null ? null : AiProviderHttpSupport.integer(usageNode, "prompt_tokens"),
                        usageNode == null ? null : AiProviderHttpSupport.integer(usageNode, "completion_tokens"),
                        null, null, null, seconds, null, null, null, Map.of()),
                status,
                text(task, "id"),
                state);
    }

    private AiModelResponse speechResponse(JsonNode root) {
        var data = root.get("data");
        var audio = data == null ? null : text(data, "audio");
        var extra = root.get("extra_info");
        var format = extra == null ? null : text(extra, "audio_format");
        var durationMs = extra == null ? null : AiProviderHttpSupport.integer(extra, "audio_length");
        var characters = extra == null ? null : AiProviderHttpSupport.integer(extra, "usage_characters");
        var outputs = new ArrayList<AiModelContent>();
        if (audio != null) {
            outputs.add(new AiModelContent.Audio(
                    null,
                    base64FromHex(audio),
                    audioMediaType(format),
                    durationMs == null ? null : Math.max(1, durationMs / 1000),
                    format,
                    null));
        }
        return new AiModelResponse(
                text(root, "trace_id"),
                null,
                outputs,
                new AiModelUsage(
                        null, null, null, null, null, null,
                        durationMs == null ? null : Math.max(1, durationMs / 1000),
                        characters, null, Map.of()),
                null,
                null,
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private AiModelResponse asrResponse(JsonNode root) {
        var text = text(root, "text");
        var duration = root.has("duration") && root.get("duration").isNumber()
                ? (int) Math.ceil(root.get("duration").asDouble())
                : null;
        return new AiModelResponse(
                text(root, "trace_id"),
                null,
                text == null ? List.of() : List.of(new AiModelContent.Text(text)),
                new AiModelUsage(null, null, null, null, null, null, duration, null, null, Map.of()),
                null,
                null,
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private AiModelResponse musicResponse(JsonNode root) {
        var data = root.get("data");
        var audio = data == null ? null : text(data, "audio");
        var extra = root.get("extra_info");
        var durationMs = extra == null ? null : AiProviderHttpSupport.integer(extra, "music_duration");
        var outputs = new ArrayList<AiModelContent>();
        if (audio != null) {
            var isUrl = audio.startsWith("http://") || audio.startsWith("https://");
            outputs.add(new AiModelContent.Audio(
                    isUrl ? audio : null,
                    isUrl ? null : base64FromHex(audio),
                    "audio/mpeg",
                    durationMs == null ? null : Math.max(1, durationMs / 1000),
                    "mp3",
                    null));
        }
        return new AiModelResponse(
                text(root, "trace_id"),
                null,
                outputs,
                new AiModelUsage(
                        null, null, null, null, null, null,
                        durationMs == null ? null : Math.max(1, durationMs / 1000),
                        null, 1, Map.of()),
                null,
                null,
                AiModelResponse.TaskStatus.SUCCEEDED);
    }

    private static AudioInput audioInput(List<AiModelContent> inputs) {
        for (var input : inputs) {
            if (input instanceof AiModelContent.Audio audio && audio.data() != null) {
                return new AudioInput(
                        "audio." + (audio.format() == null ? "mp3" : audio.format()),
                        audio.mediaType() == null ? "audio/mpeg" : audio.mediaType(),
                        Base64.getDecoder().decode(audio.data()));
            }
            if (input instanceof AiModelContent.File file && file.data() != null) {
                return new AudioInput(
                        file.filename() == null ? "audio.mp3" : file.filename(),
                        file.mediaType() == null ? "audio/mpeg" : file.mediaType(),
                        Base64.getDecoder().decode(file.data()));
            }
        }
        return null;
    }

    private static String imageUrl(AiModelContent.Image image) {
        if (image.url() != null) {
            return image.url();
        }
        if (image.fileId() != null) {
            return "mm_file://" + image.fileId();
        }
        if (image.data() != null) {
            var mediaType = image.mediaType() == null ? "image/png" : image.mediaType();
            return "data:" + mediaType + ";base64," + image.data();
        }
        return "";
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

    private static String mediaType(JsonNode root) {
        var format = text(root, "output_format");
        return "png".equals(format) ? "image/png" : "jpeg".equals(format) ? "image/jpeg" : null;
    }

    private static String audioMediaType(String format) {
        return "wav".equals(format) ? "audio/wav" : "pcm".equals(format) ? "audio/pcm" : "audio/mpeg";
    }

    private static String base64FromHex(String hex) {
        try {
            return Base64.getEncoder().encodeToString(HexFormat.of().parseHex(hex));
        } catch (IllegalArgumentException error) {
            return hex;
        }
    }

    private static String modelCode(AiModel model) {
        return model == null || model.getSpec() == null ? "" : model.getSpec().getModelCode();
    }

    private static @Nullable String text(JsonNode node, String field) {
        return AiProviderHttpSupport.text(node, field);
    }

    private record AudioInput(String filename, String mediaType, byte[] bytes) {}
}
