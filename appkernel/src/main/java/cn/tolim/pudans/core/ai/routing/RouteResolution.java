package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.ai.AiChatMode;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.ChatModes;
import cn.tolim.pudans.core.ai.config.RouteGroups;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.core.ai.gateway.AiTokenEstimator;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.AiProperties;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RouteResolution {

    private static final String CODE_MODE_NOT_FOUND = "ai-mode-not-found";

    private static final String CODE_GROUP_MISSING = "ai-route-group-missing";

    private static final String CODE_NO_CANDIDATE = "ai-route-no-candidate";

    private static final String CODE_GROUP_MODE_MISMATCH = "ai-route-group-mode-mismatch";

    private static final String MODE_FAST = "fast";

    private static final String MODE_PRO = "pro";

    private static final int MAX_FALLBACK_DEPTH = 3;

    private final ChatModes chatModes;

    private final RouteGroups routeGroups;

    private final AiSettings aiSettings;

    private final RouteCandidates routeCandidates;

    private final RouteEngine routeEngine;

    private final AiProperties properties;

    public Mono<Resolution> resolve(Input input) {
        return modeAndGroup(input)
                .flatMap(modeAndGroup -> resolveGroupChain(
                        input,
                        modeAndGroup.mode(),
                        modeAndGroup.group(),
                        Set.of(groupName(modeAndGroup.group())),
                        0));
    }

    private Mono<ModeAndGroup> modeAndGroup(Input input) {
        return resolveMode(input.modeCode())
                .flatMap(mode -> resolveGroup(input.routeGroupCode(), mode)
                        .flatMap(group -> validateParentMode(mode, group))
                        .map(group -> new ModeAndGroup(mode, group)))
                .switchIfEmpty(Mono.defer(() -> resolveGroup(input.routeGroupCode(), null)
                        .map(group -> new ModeAndGroup(null, group))));
    }

    private Mono<Resolution> resolveGroupChain(
            Input input, @Nullable AiChatMode mode, AiRouteGroup group, Set<String> visited, int depth) {
        var modeAndGroup = new ModeAndGroup(mode, group);
        return route(input, modeAndGroup)
                .flatMap(resolution -> resolveNextFallback(input, mode, group, visited, depth)
                        .map(fallback -> attachFallback(resolution, fallback))
                        .switchIfEmpty(Mono.just(resolution)))
                .onErrorResume(error -> isNoCandidate(error)
                        ? resolveNextFallback(input, mode, group, visited, depth)
                                .map(fallback -> withFallbackChain(fallback, groupName(group)))
                                .switchIfEmpty(Mono.defer(() -> Mono.error(error)))
                        : Mono.error(error));
    }

    // 组间 fallback 最多三层并跳过已访问组，避免环路
    private Mono<Resolution> resolveNextFallback(
            Input input, @Nullable AiChatMode mode, AiRouteGroup group, Set<String> visited, int depth) {
        if (depth >= MAX_FALLBACK_DEPTH) {
            return Mono.empty();
        }
        var fallback = group.getSpec() == null ? null : group.getSpec().getFallback();
        if (fallback == null || fallback.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(fallback)
                .filter(StringUtils::hasText)
                .distinct()
                .filter(name -> !visited.contains(name))
                .concatMap(name -> routeGroups
                        .get(name)
                        .filter(candidate -> candidate.getSpec() != null && candidate.getSpec().isEnabled())
                        .flatMap(candidate -> {
                            var nextVisited = new HashSet<>(visited);
                            nextVisited.add(name);
                            return resolveGroupChain(input, mode, candidate, nextVisited, depth + 1)
                                    .onErrorResume(error -> Mono.empty());
                        })
                        .onErrorResume(error -> Mono.empty()))
                .next();
    }

    private static Resolution attachFallback(Resolution primary, Resolution fallback) {
        var groups = new ArrayList<String>();
        collectFallbackGroups(fallback, groups);
        var trace = primary.plan().trace().withFallbackGroups(groups);
        var plan = new RoutePlan(
                primary.plan().selected(),
                primary.plan().fallbackSeats(),
                primary.plan().fallbackReferences(),
                trace);
        return new Resolution(
                primary.routeGroup(),
                primary.chatMode(),
                primary.request(),
                plan,
                primary.seats(),
                fallback);
    }

    private static void collectFallbackGroups(Resolution resolution, List<String> groups) {
        if (resolution == null) {
            return;
        }
        groups.add(groupName(resolution.routeGroup()));
        collectFallbackGroups(resolution.fallbackGroup(), groups);
    }

    // 主组无候选时返回的解析属于 fallback 组，需要把整条链路补回 trace
    private static Resolution withFallbackChain(Resolution resolution, String originalGroup) {
        var groups = new ArrayList<String>();
        groups.add(originalGroup);
        var current = resolution;
        while (current != null) {
            groups.add(groupName(current.routeGroup()));
            current = current.fallbackGroup();
        }
        var trace = resolution.plan().trace().withFallbackGroups(groups);
        var plan = new RoutePlan(
                resolution.plan().selected(),
                resolution.plan().fallbackSeats(),
                resolution.plan().fallbackReferences(),
                trace);
        return new Resolution(
                resolution.routeGroup(),
                resolution.chatMode(),
                resolution.request(),
                plan,
                resolution.seats(),
                resolution.fallbackGroup());
    }

    private static String groupName(AiRouteGroup group) {
        return group.getMetadata() == null ? "" : group.getMetadata().getName();
    }

    private static boolean isNoCandidate(Throwable error) {
        return error instanceof ApiException apiException && CODE_NO_CANDIDATE.equals(apiException.getCode());
    }

    // 固定模式只接受匹配父模式的路由组，NONE 组不得隐式选择
    private Mono<AiRouteGroup> validateParentMode(AiChatMode mode, AiRouteGroup group) {
        var expected = fixedParentMode(mode);
        if (expected == null || group.effectiveParentMode() == expected) {
            return Mono.just(group);
        }
        var groupName = group.getMetadata() == null ? "" : group.getMetadata().getName();
        return Mono.error(new ApiException(
                HttpStatus.BAD_REQUEST,
                CODE_GROUP_MODE_MISMATCH,
                "路由组 " + groupName + " 的父模式与对话模式 " + mode.getMetadata().getName() + " 不匹配。"));
    }

    private static AiRouteGroup.@Nullable ParentMode fixedParentMode(AiChatMode mode) {
        var name = mode.getMetadata() == null ? null : mode.getMetadata().getName();
        return switch (name == null ? "" : name) {
            case MODE_FAST -> AiRouteGroup.ParentMode.FAST;
            case MODE_PRO -> AiRouteGroup.ParentMode.PRO;
            default -> null;
        };
    }

    private Mono<Resolution> route(Input input, ModeAndGroup modeAndGroup) {
        var group = modeAndGroup.group();
        if (group.getSpec() == null || !group.getSpec().isEnabled()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_GROUP_MISSING, "路由组不可用。"));
        }
        return routeCandidates
                .expand(
                        group,
                        Set.of(AiModel.Capability.CHAT),
                        inputModalities(input.ir()),
                        Set.of(AiModel.Modality.TEXT))
                .flatMap(seats -> {
                    var request = routeRequest(input, modeAndGroup.mode(), group);
                    var plan = routeEngine.route(request, seats);
                    if (!plan.routed()) {
                        return Mono.error(new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE, CODE_NO_CANDIDATE, noCandidateMessage(plan)));
                    }
                    return Mono.just(new Resolution(
                            group, modeAndGroup.mode(), request, plan, seats));
                });
    }

    private Mono<AiChatMode> resolveMode(@Nullable String modeCode) {
        var requested = StringUtils.hasText(modeCode);
        var code = requested
                ? Mono.just(modeCode)
                : aiSettings.spec().flatMap(spec -> Mono.justOrEmpty(spec.getDefaultChatMode()));
        return code.filter(StringUtils::hasText)
                .flatMap(chatModes::get)
                .onErrorMap(
                        ExtensionNotFoundException.class,
                        error -> new ApiException(
                                HttpStatus.NOT_FOUND, CODE_MODE_NOT_FOUND, "对话模式不存在。"))
                .filter(mode -> mode.getSpec() != null && mode.getSpec().isEnabled())
                .switchIfEmpty(Mono.defer(() -> requested
                        ? Mono.error(new ApiException(
                                HttpStatus.NOT_FOUND, CODE_MODE_NOT_FOUND, "对话模式不存在或已禁用。"))
                        : Mono.empty()));
    }


    public Mono<Resolution> resolveCapability(CapabilityInput input) {
        return resolveGroup(input.routeGroupCode(), null)
                .flatMap(group -> routeCapability(input, group));
    }

    private Mono<Resolution> routeCapability(CapabilityInput input, AiRouteGroup group) {
        if (group.getSpec() == null || !group.getSpec().isEnabled()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_GROUP_MISSING, "路由组不可用。"));
        }
        return routeCandidates
                .expand(
                        group,
                        input.capabilities(),
                        input.inputModalities(),
                        input.outputModalities())
                .flatMap(seats -> {
                    var request = capabilityRequest(input, group);
                    var plan = routeEngine.route(request, seats);
                    if (!plan.routed()) {
                        return Mono.error(new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE, CODE_NO_CANDIDATE, noCandidateMessage(plan)));
                    }
                    return Mono.just(new Resolution(group, null, request, plan, seats));
                });
    }

    private RouteRequest capabilityRequest(CapabilityInput input, AiRouteGroup group) {
        var spec = group.getSpec();
        var builder = RouteRequest.builder()
                .routeMode(spec.getRouteMode())
                .requestedTier(input.requestedTier())
                .routeGroupDefaultTier(spec.getDefaultTier())
                .affinity(spec.getAffinity())
                .previousSelection(input.previousSelection())
                .requiredCapabilities(input.capabilities())
                .requiredInputModalities(input.inputModalities())
                .requiredOutputModalities(input.outputModalities())
                .fallbackReferences(spec.getFallback())
                .manualPick(spec.getManualPick())
                .healthThreshold(properties.getRouting().getHealthThreshold())
                .qualityThreshold(spec.getQualityThreshold() == null ? 0.6 : spec.getQualityThreshold())
                .classifierEnabled(false);
        applyGroupPolicy(group, builder);
        return builder.build();
    }

    private Mono<AiRouteGroup> resolveGroup(@Nullable String routeGroupCode, @Nullable AiChatMode mode) {
        if (StringUtils.hasText(routeGroupCode)) {
            return routeGroups.get(routeGroupCode);
        }
        var modeGroup = mode == null || mode.getSpec() == null ? null : mode.getSpec().getRouteGroup();
        if (StringUtils.hasText(modeGroup)) {
            return routeGroups.get(modeGroup);
        }
        return routeGroups
                .defaultGroup()
                .onErrorMap(
                        IllegalStateException.class,
                        error -> new ApiException(
                                HttpStatus.BAD_REQUEST, CODE_GROUP_MISSING, "未配置默认路由组。"));
    }

    private RouteRequest routeRequest(Input input, @Nullable AiChatMode mode, AiRouteGroup group) {
        var spec = group.getSpec();
        var params = input.ir().requestParams();
        var settings = input.setting();
        var routing = settings == null || settings.getRouting() == null
                ? new AiSetting.Routing()
                : settings.getRouting();
        var maxOutput = params.maxTokens() == null ? 0 : Math.max(0, params.maxTokens());
        var modeTier = mode == null || mode.getSpec() == null ? null : mode.getSpec().getDefaultTier();
        var builder = RouteRequest.builder()
                .routeMode(spec.getRouteMode())
                .requestedTier(input.requestedTier())
                .chatModeDefaultTier(modeTier)
                .routeGroupDefaultTier(spec.getDefaultTier())
                .affinity(spec.getAffinity())
                .previousSelection(input.previousSelection())
                .agentName(input.agentName())
                .imageCount(imageCount(input.ir()))
                .requestedModel(input.modelOverride() == null ? params.model() : input.modelOverride())
                .explicitModel(input.modelOverride())
                .promptTokens(AiTokenEstimator.estimateMessages(input.ir().messages()))
                .maxOutputTokens(maxOutput)
                .requiredCapabilities(capabilities(input.ir()))
                .requiredInputModalities(inputModalities(input.ir()))
                .requiredOutputModalities(Set.of(AiModel.Modality.TEXT))
                .fallbackReferences(spec.getFallback())
                .manualPick(spec.getManualPick())
                .healthThreshold(properties.getRouting().getHealthThreshold())
                .qualityThreshold(spec.getQualityThreshold() == null ? 0.6 : spec.getQualityThreshold())
                .classifierEnabled(routing.isClassifierEnabled())
                .classifierFallbackTier(routing.getClassifierFallbackTier())
                .lastUserMessage(lastUserText(input.ir()));
        applyGroupPolicy(group, builder);
        return builder.build();
    }

    // 路由组的规则、上下文模式、耐心与沉降开关随请求进入引擎
    private static void applyGroupPolicy(AiRouteGroup group, RouteRequest.RouteRequestBuilder builder) {
        var spec = group.getSpec();
        builder.contextMode(spec.getContextMode())
                .patienceSeconds(spec.getPatienceSeconds() == null ? 0 : spec.getPatienceSeconds())
                .firstTokenSeconds(spec.getFirstTokenSeconds() == null ? 0 : spec.getFirstTokenSeconds())
                .sink(spec.isSink())
                .rules(spec.getRules() == null ? List.of() : spec.getRules());
    }

    private static Set<AiModel.Capability> capabilities(AiChatIr ir) {
        var capabilities = EnumSet.noneOf(AiModel.Capability.class);
        if (ir.stream()) {
            capabilities.add(AiModel.Capability.STREAM);
        }
        if (!ir.tools().isEmpty()) {
            capabilities.add(AiModel.Capability.TOOLS);
        }
        if (ir.requestParams().extra().containsKey("response_format")) {
            capabilities.add(AiModel.Capability.JSON);
        }
        if (hasImage(ir)) {
            capabilities.add(AiModel.Capability.VISION);
        }
        if (hasMedia(ir, "audio/")) {
            capabilities.add(AiModel.Capability.AUDIO_UNDERSTANDING);
        }
        if (hasMedia(ir, "video/")) {
            capabilities.add(AiModel.Capability.VIDEO_UNDERSTANDING);
        }
        return capabilities;
    }

    private static boolean hasImage(AiChatIr ir) {
        return imageCount(ir) > 0;
    }

    private static int imageCount(AiChatIr ir) {
        return (int) ir.messages().stream()
                .flatMap(message -> message.parts().stream())
                .filter(part -> part instanceof AiContentPart.Image
                        || (part instanceof AiContentPart.File file
                                && file.mediaType() != null
                                && file.mediaType().startsWith("image/")))
                .count();
    }

    private static boolean hasMedia(AiChatIr ir, String prefix) {
        return ir.messages().stream()
                .flatMap(message -> message.parts().stream())
                .filter(AiContentPart.File.class::isInstance)
                .map(AiContentPart.File.class::cast)
                .anyMatch(file -> file.mediaType() != null && file.mediaType().startsWith(prefix));
    }

    private static Set<AiModel.Modality> inputModalities(AiChatIr ir) {
        var modalities = EnumSet.of(AiModel.Modality.TEXT);
        for (var message : ir.messages()) {
            for (var part : message.parts()) {
                if (part instanceof AiContentPart.Image) {
                    modalities.add(AiModel.Modality.IMAGE);
                } else if (part instanceof AiContentPart.File file && file.mediaType() != null) {
                    var mediaType = file.mediaType();
                    if (mediaType.startsWith("image/")) {
                        modalities.add(AiModel.Modality.IMAGE);
                    } else if (mediaType.startsWith("audio/")) {
                        modalities.add(AiModel.Modality.AUDIO);
                    } else if (mediaType.startsWith("video/")) {
                        modalities.add(AiModel.Modality.VIDEO);
                    } else {
                        modalities.add(AiModel.Modality.FILE);
                    }
                }
            }
        }
        return modalities;
    }

    private static String noCandidateMessage(RoutePlan plan) {
        var candidates = plan.trace() == null ? List.<RouteTrace.Candidate>of() : plan.trace().candidates();
        if (candidates.isEmpty()) {
            return "路由组没有可用候选：没有已启用且满足本次请求能力的模型账号组合。";
        }
        var details = candidates.stream()
                .limit(3)
                .map(candidate -> candidate.provider() + "/" + candidate.account() + "/" + candidate.model()
                        + "（" + eliminationLabel(candidate.reason()) + "）")
                .collect(Collectors.joining("、"));
        return "路由组没有可用候选：" + details + "。";
    }

    private static String eliminationLabel(@Nullable RouteEliminationReason reason) {
        if (reason == null) {
            return "未命中固定账号";
        }
        return switch (reason) {
            case DISABLED -> "已禁用";
            case RESTING -> "熔断冷却中，稍后自动恢复";
            case UNHEALTHY -> "健康度过低";
            case CAPABILITY_MISMATCH -> "模型能力或附件类型不匹配";
            case CONTEXT_EXCEEDED -> "上下文超出模型窗口";
            case SPENT -> "额度已用尽";
            case SUNK -> "近期被限流而沉底";
        };
    }

    private static String lastUserText(AiChatIr ir) {
        var messages = ir.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            var message = messages.get(i);
            if (!"user".equals(message.role())) {
                continue;
            }
            var text = message.parts().stream()
                    .filter(AiContentPart.Text.class::isInstance)
                    .map(AiContentPart.Text.class::cast)
                    .map(AiContentPart.Text::text)
                    .filter(value -> !value.isBlank())
                    .reduce((left, right) -> right);
            if (text.isPresent()) {
                return text.get();
            }
        }
        return null;
    }

    private record ModeAndGroup(@Nullable AiChatMode mode, AiRouteGroup group) {}

    public record Input(
            String modeCode,
            String routeGroupCode,
            AiModel.Tier requestedTier,
            AiChatIr ir,
            AiSetting.Spec setting,
            @Nullable RouteSelection previousSelection,
            @Nullable String modelOverride,
            @Nullable String agentName) {

        public Input(
                String modeCode,
                String routeGroupCode,
                AiModel.Tier requestedTier,
                AiChatIr ir,
                AiSetting.Spec setting,
                @Nullable RouteSelection previousSelection,
                @Nullable String modelOverride) {
            this(
                    modeCode,
                    routeGroupCode,
                    requestedTier,
                    ir,
                    setting,
                    previousSelection,
                    modelOverride,
                    null);
        }
    }

    public record CapabilityInput(
            @Nullable String routeGroupCode,
            Set<AiModel.Capability> capabilities,
            Set<AiModel.Modality> inputModalities,
            Set<AiModel.Modality> outputModalities,
            AiModel.@Nullable Tier requestedTier,
            @Nullable RouteSelection previousSelection) {

        public CapabilityInput {
            capabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            inputModalities = inputModalities == null ? Set.of() : Set.copyOf(inputModalities);
            outputModalities = outputModalities == null ? Set.of() : Set.copyOf(outputModalities);
        }
    }

    public record Resolution(
            AiRouteGroup routeGroup,
            @Nullable AiChatMode chatMode,
            RouteRequest request,
            RoutePlan plan,
            List<RouteSeat> seats,
            @Nullable Resolution fallbackGroup) {

        public Resolution {
            seats = seats == null ? List.of() : List.copyOf(seats);
        }

        public Resolution(
                AiRouteGroup routeGroup,
                @Nullable AiChatMode chatMode,
                RouteRequest request,
                RoutePlan plan,
                List<RouteSeat> seats) {
            this(routeGroup, chatMode, request, plan, seats, null);
        }

        public RouteSelection selection() {
            return plan.selected();
        }
    }
}
