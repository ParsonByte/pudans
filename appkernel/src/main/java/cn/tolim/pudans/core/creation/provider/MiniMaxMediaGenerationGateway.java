package cn.tolim.pudans.core.creation.provider;

import cn.tolim.pudans.contract.ai.dto.AiImage;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiImageInput;
import cn.tolim.pudans.contract.ai.dto.AiVideoContent;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationTask;
import cn.tolim.pudans.contract.ai.dto.AiVideoMedia;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskSubmitted;
import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.core.ai.gateway.AiChannelSecrets;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxGateway;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MiniMaxMediaGenerationGateway implements MediaGenerationGateway {

    private static final String PROVIDER = "minimax";

    private static final Duration RESULT_DOWNLOAD_TIMEOUT = Duration.ofMinutes(30);

    private static final int MAX_INPUT_BYTES = 100 * 1024 * 1024;

    private static final Set<String> IMAGE_RATIOS = Set.of(
            "1:1", "16:9", "4:3", "3:2", "2:3", "3:4", "9:16", "21:9");

    private static final Set<String> VIDEO_RATIOS =
            Set.of("adaptive", "21:9", "16:9", "4:3", "1:1", "3:4", "9:16");

    private final MiniMaxGateway gateway;

    private final AiChannelRepository channels;

    private final AiChannelSecrets secrets;

    private final AssetRepository assets;

    private final StorageTransfer storageTransfer;

    private final AiProviderHttp http;

    @Override
    public String provider() {
        return PROVIDER;
    }

    @Override
    public Mono<Submission> submit(Request request) {
        return Mono.defer(() -> {
            if (request == null) {
                return Mono.error(localFailure(
                        "generation-request-required", "生成请求不能为空。"));
            }
            if (request.mediaType() == GenerationTask.MediaType.IMAGE) {
                return submitImage(request);
            }
            if (request.mediaType() == GenerationTask.MediaType.VIDEO) {
                return submitVideo(request);
            }
            return Mono.error(localFailure(
                    "generation-media-type-unsupported", "当前媒体类型不受支持。"));
        });
    }

    @Override
    public Mono<State> find(Reference reference) {
        return Mono.defer(() -> {
            if (reference == null || reference.mediaType() != GenerationTask.MediaType.VIDEO) {
                return Mono.just(new State(
                        Phase.UNKNOWN,
                        reference == null ? null : reference.providerTaskId(),
                        reference == null ? null : reference.providerRequestId(),
                        "generation-provider-task-unknown",
                        "图片生成结果无法恢复查询。",
                        null));
            }
            return resolve(reference.channelId())
                    .flatMap(channel -> gateway.videoTask(
                            channel.channel(), channel.credentials(), reference.providerTaskId()))
                    .map(task -> videoState(reference, task));
        });
    }

    @Override
    public Mono<Void> cancel(Reference reference) {
        if (reference == null
                || reference.mediaType() != GenerationTask.MediaType.VIDEO
                || !StringUtils.hasText(reference.providerTaskId())) {
            return Mono.empty();
        }
        return resolve(reference.channelId())
                .flatMap(channel -> gateway.deleteVideoTask(
                        channel.channel(), channel.credentials(), reference.providerTaskId()))
                .then();
    }

    private Mono<Submission> submitImage(Request request) {
        return resolve(request.channelId()).flatMap(channel -> inputs(request)
                .flatMap(inputs -> {
                    var settings = settings(request);
                    var body = new AiImageGenerationRequest(
                            request.providerModel(),
                            request.prompt(),
                            null,
                            quantity(settings),
                            null,
                            imageRatio(settings.get("ratio")),
                            imageArea(settings.get("resolution")),
                            "png",
                            "url",
                            null,
                            null,
                            null,
                            inputs.stream().map(AiImageInput::new).toList());
                    var result = inputs.isEmpty()
                            ? gateway.imageGenerations(
                                    channel.channel(), channel.credentials(), body)
                            : gateway.imageEdits(
                                    channel.channel(), channel.credentials(), body);
                    return result.flatMap(image -> imageState(request, image));
                })
                .map(state -> new Submission(
                        state.providerTaskId(), state.providerRequestId(), channel.channel().getId(), state)));
    }

    private Mono<Submission> submitVideo(Request request) {
        return resolve(request.channelId()).flatMap(channel -> inputs(request)
                .flatMap(inputs -> {
                    var settings = settings(request);
                    var body = new AiVideoGenerationRequest(
                            request.providerModel(),
                            videoContent(request, inputs),
                            videoResolution(settings.get("resolution")),
                            videoDuration(settings.get("duration")),
                            videoRatio(settings.get("ratio")),
                            Map.of(),
                            Boolean.TRUE.equals(settings.get("audio")),
                            null,
                            null);
                    return gateway
                            .videoGeneration(channel.channel(), channel.credentials(), body)
                            .flatMap(submitted -> videoSubmission(request, channel, submitted));
                }));
    }

    private Mono<Submission> videoSubmission(
            Request request, ResolvedChannel channel, AiVideoTaskSubmitted submitted) {
        var taskId = submitted == null ? null : submitted.taskId();
        if (!StringUtils.hasText(taskId)) {
            return Mono.error(providerFailure(
                    "generation-provider-submission-invalid",
                    "供应商没有返回视频任务标识。"));
        }
        var state = new State(Phase.PENDING, taskId, null, null, null, null);
        return Mono.just(new Submission(taskId, null, channel.channel().getId(), state));
    }

    private Mono<State> imageState(Request request, AiImageGenerationResult result) {
        var images = result == null ? List.<AiImage>of() : result.data();
        if (images.isEmpty()) {
            return Mono.error(providerFailure(
                    "generation-provider-result-missing", "供应商没有返回图片结果。"));
        }
        var requestId = StringUtils.hasText(result.requestId())
                ? result.requestId()
                : request.clientReference();
        var values = new ArrayList<Asset>();
        for (var index = 0; index < images.size(); index++) {
            var image = images.get(index);
            if (image == null) {
                continue;
            }
            var assetId = requestId + "-" + index;
            if (StringUtils.hasText(image.b64Json())) {
                byte[] bytes;
                try {
                    bytes = Base64.getDecoder().decode(image.b64Json());
                } catch (IllegalArgumentException error) {
                    return Mono.error(providerFailure(
                            "generation-provider-result-invalid",
                            "供应商返回的图片内容无法解码。"));
                }
                values.add(new Asset(
                        assetId,
                        "image-" + (index + 1) + ".png",
                        "image/png",
                        bytes.length,
                        buffer(bytes)));
            } else if (StringUtils.hasText(image.url())) {
                values.add(new Asset(
                        assetId,
                        "image-" + (index + 1) + "." + imageExtension(image.url(), result.outputFormat()),
                        imageContentType(image.url(), result.outputFormat()),
                        0,
                        http.getBinaryStream(URI.create(image.url()), RESULT_DOWNLOAD_TIMEOUT)));
            }
        }
        if (values.isEmpty()) {
            return Mono.error(providerFailure(
                    "generation-provider-result-invalid", "供应商返回的图片结果缺少地址或内容。"));
        }
        return Mono.just(new State(
                Phase.SUCCEEDED,
                requestId,
                requestId,
                null,
                null,
                new Result(values)));
    }

    private State videoState(Reference reference, AiVideoGenerationTask task) {
        if (task == null) {
            return new State(
                    Phase.UNKNOWN,
                    reference.providerTaskId(),
                    reference.providerRequestId(),
                    "generation-provider-task-unknown",
                    "供应商没有返回视频任务状态。",
                    null);
        }
        var taskId = StringUtils.hasText(task.id())
                ? task.id()
                : reference.providerTaskId();
        var status = task.status() == null ? "" : task.status().trim().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "queued", "queueing", "pending", "submitted" -> new State(
                    Phase.PENDING, taskId, reference.providerRequestId(), null, null, null);
            case "processing", "running", "in_progress" -> new State(
                    Phase.RUNNING, taskId, reference.providerRequestId(), null, null, null);
            case "succeeded", "success", "completed", "complete" -> videoResult(taskId, task);
            case "failed", "fail", "error" -> new State(
                    Phase.FAILED,
                    taskId,
                    reference.providerRequestId(),
                    task.error() == null || !StringUtils.hasText(task.error().code())
                            ? "generation-provider-failed"
                            : task.error().code(),
                    task.error() == null || !StringUtils.hasText(task.error().message())
                            ? "供应商任务执行失败。"
                            : task.error().message(),
                    null);
            case "cancelled", "canceled", "deleted" -> new State(
                    Phase.CANCELLED, taskId, reference.providerRequestId(), null, null, null);
            default -> new State(
                    Phase.UNKNOWN,
                    taskId,
                    reference.providerRequestId(),
                    "generation-provider-task-unknown",
                    "无法确认供应商任务状态。",
                    null);
        };
    }

    private State videoResult(String taskId, AiVideoGenerationTask task) {
        var url = task.content() == null ? null : task.content().url();
        if (!StringUtils.hasText(url)) {
            return new State(
                    Phase.SUCCEEDED,
                    taskId,
                    null,
                    null,
                    null,
                    new Result(List.of()));
        }
        var asset = new Asset(
                taskId + "-video",
                "video.mp4",
                "video/mp4",
                0,
                http.getBinaryStream(URI.create(url), RESULT_DOWNLOAD_TIMEOUT));
        return new State(
                Phase.SUCCEEDED,
                taskId,
                null,
                null,
                null,
                new Result(List.of(asset)));
    }

    private Mono<List<String>> inputs(Request request) {
        var ids = new LinkedHashSet<>(request.inputAssetIds() == null
                ? List.<String>of()
                : request.inputAssetIds());
        if (ids.isEmpty()) {
            return Mono.just(List.of());
        }
        var identity = new RequestIdentity(request.ownerId(), request.workspaceId());
        var values = new ArrayList<String>();
        return Flux.fromIterable(ids)
                .concatMap(assetId -> inputDataUrl(assetId, identity))
                .doOnNext(values::add)
                .then(Mono.just(values));
    }

    private Mono<String> inputDataUrl(String assetId, RequestIdentity identity) {
        UUID id;
        try {
            id = UUID.fromString(assetId);
        } catch (RuntimeException error) {
            return Mono.error(localFailure(
                    "generation-input-asset-invalid", "输入资产标识无效。"));
        }
        return assets
                .findOwned(id, identity)
                .switchIfEmpty(Mono.error(localFailure(
                        "generation-input-asset-not-found", "输入资产不存在。")))
                .flatMap(asset -> storageTransfer
                        .readBytes(asset.storageFileId(), MAX_INPUT_BYTES)
                        .map(bytes -> dataUrl(asset.contentType(), bytes)));
    }

    private Mono<ResolvedChannel> resolve(UUID channelId) {
        if (channelId == null) {
            return Mono.error(localFailure(
                    "generation-provider-channel-missing", "生成任务缺少供应商渠道。"));
        }
        return channels
                .findById(channelId)
                .switchIfEmpty(Mono.error(localFailure(
                        "generation-provider-channel-not-found", "生成渠道不存在。")))
                .flatMap(channel -> {
                    if (!PROVIDER.equals(channel.getProvider())
                            || !AiSupport.ACTIVE.equals(channel.getStatus())) {
                        return Mono.error(localFailure(
                                "generation-provider-channel-unavailable", "生成渠道不可用。"));
                    }
                    return secrets
                            .resolve(channel)
                            .map(credentials -> new ResolvedChannel(channel, credentials));
                });
    }

    private static List<AiVideoContent> videoContent(Request request, List<String> inputs) {
        var values = new ArrayList<AiVideoContent>();
        values.add(new AiVideoContent("text", request.prompt(), null, null, null, null));
        for (var index = 0; index < inputs.size(); index++) {
            var role = request.operation() == GenerationTask.Operation.FRAME
                    ? index == 0 ? "first_frame" : "last_frame"
                    : "reference";
            values.add(new AiVideoContent(
                    "image_url", null, new AiVideoMedia(inputs.get(index)), null, null, role));
        }
        return List.copyOf(values);
    }

    private static int quantity(Map<String, Object> settings) {
        var value = settings == null ? null : settings.get("quantity");
        if (!(value instanceof Number number)) {
            return 1;
        }
        return Math.max(1, Math.min(9, number.intValue()));
    }

    private static Map<String, Object> settings(Request request) {
        return request.settings() == null ? Map.of() : request.settings();
    }

    private static String imageArea(Object value) {
        var resolution = text(value);
        if (resolution == null) {
            return "1K";
        }
        return switch (resolution) {
            case "1080P" -> "1K";
            case "1.5K", "2K" -> "2K";
            default -> "1K";
        };
    }

    private static String imageRatio(Object value) {
        var ratio = text(value);
        if (ratio == null || "自动".equals(ratio) || "auto".equalsIgnoreCase(ratio)) {
            return null;
        }
        if (IMAGE_RATIOS.contains(ratio)) {
            return ratio;
        }
        return switch (ratio) {
            case "5:4" -> "4:3";
            case "4:5" -> "3:4";
            default -> null;
        };
    }

    private static String videoResolution(Object value) {
        var resolution = text(value);
        if (resolution == null) {
            return "768P";
        }
        return switch (resolution) {
            case "360P", "540P" -> "480P";
            case "720P" -> "768P";
            case "1080P" -> "2K";
            case "480P", "768P", "2K" -> resolution;
            default -> "768P";
        };
    }

    private static int videoDuration(Object value) {
        var duration = value instanceof Number number ? number.intValue() : 5;
        return Math.max(4, Math.min(15, duration));
    }

    private static String videoRatio(Object value) {
        var ratio = text(value);
        if (ratio == null) {
            return "16:9";
        }
        if (VIDEO_RATIOS.contains(ratio)) {
            return ratio;
        }
        return switch (ratio) {
            case "3:2" -> "4:3";
            case "2:3" -> "3:4";
            case "自动", "auto" -> "adaptive";
            default -> "16:9";
        };
    }

    private static String dataUrl(String contentType, byte[] bytes) {
        var type = StringUtils.hasText(contentType) ? contentType : "image/png";
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    private static String imageContentType(String url, String outputFormat) {
        var format = StringUtils.hasText(outputFormat)
                ? outputFormat.trim().toLowerCase(Locale.ROOT)
                : imageExtension(url, null);
        return switch (format) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "image/png";
        };
    }

    private static String imageExtension(String url, String outputFormat) {
        if (StringUtils.hasText(outputFormat)) {
            var value = outputFormat.trim().toLowerCase(Locale.ROOT);
            return "jpg".equals(value) ? "jpeg" : value;
        }
        var path = URI.create(url).getPath();
        var dot = path == null ? -1 : path.lastIndexOf('.');
        return dot < 0 ? "png" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null || !StringUtils.hasText(value.toString())
                ? null
                : value.toString().trim();
    }

    private static Flux<DataBuffer> buffer(byte[] bytes) {
        return Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes));
    }

    private static Failure localFailure(String code, String message) {
        return new Failure(code, message, false);
    }

    private static Failure providerFailure(String code, String message) {
        return new Failure(code, message, true);
    }

    private record ResolvedChannel(AiChannelEntity channel, Map<String, String> credentials) {}
}
