package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.core.extension.ai.AiChatMode;
import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.core.extension.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiChatModeService;
import cn.tolim.pudans.core.ai.config.AiRouteGroupService;
import cn.tolim.pudans.core.ai.config.AiSettingService;
import cn.tolim.pudans.core.ai.gateway.AiTokenEstimator;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.extension.exception.ExtensionNotFoundException;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiRouteResolutionService {

    private static final String CODE_MODE_NOT_FOUND = "ai-mode-not-found";

    private static final String CODE_GROUP_MISSING = "ai-route-group-missing";

    private static final String CODE_NO_CANDIDATE = "ai-route-no-candidate";

    private final AiChatModeService chatModeService;

    private final AiRouteGroupService routeGroupService;

    private final AiSettingService settingService;

    private final RouteCandidateService candidateService;

    private final RouteEngine routeEngine;

    private final AiProperties properties;

    public Mono<Resolution> resolve(Input input) {
        return resolveMode(input.modeCode())
                .flatMap(mode -> resolveGroup(input.routeGroupCode(), mode)
                        .map(group -> new ModeAndGroup(mode, group)))
                .switchIfEmpty(Mono.defer(() -> resolveGroup(input.routeGroupCode(), null)
                        .map(group -> new ModeAndGroup(null, group))))
                .flatMap(modeAndGroup -> route(input, modeAndGroup));
    }

    private Mono<Resolution> route(Input input, ModeAndGroup modeAndGroup) {
        var group = modeAndGroup.group();
        if (group.getSpec() == null || !group.getSpec().isEnabled()) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, CODE_GROUP_MISSING, "路由组不可用。"));
        }
        return candidateService
                .expand(group)
                .flatMap(seats -> {
                    var request = routeRequest(input, modeAndGroup.mode(), group);
                    var plan = routeEngine.route(request, seats);
                    if (!plan.routed()) {
                        return Mono.error(new ApiException(
                                HttpStatus.SERVICE_UNAVAILABLE, CODE_NO_CANDIDATE, "路由组没有可用候选。"));
                    }
                    return Mono.just(new Resolution(
                            group, modeAndGroup.mode(), request, plan, seats));
                });
    }

    private Mono<AiChatMode> resolveMode(@Nullable String modeCode) {
        var requested = StringUtils.hasText(modeCode);
        var code = requested
                ? Mono.just(modeCode)
                : settingService.spec().flatMap(spec -> Mono.justOrEmpty(spec.getDefaultChatMode()));
        return code.filter(StringUtils::hasText)
                .flatMap(chatModeService::get)
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
        return candidateService
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
                                HttpStatus.SERVICE_UNAVAILABLE, CODE_NO_CANDIDATE, "路由组没有可用候选。"));
                    }
                    return Mono.just(new Resolution(group, null, request, plan, seats));
                });
    }

    private RouteRequest capabilityRequest(CapabilityInput input, AiRouteGroup group) {
        var spec = group.getSpec();
        return RouteRequest.builder()
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
                .classifierEnabled(false)
                .build();
    }

    private Mono<AiRouteGroup> resolveGroup(@Nullable String routeGroupCode, @Nullable AiChatMode mode) {
        if (StringUtils.hasText(routeGroupCode)) {
            return routeGroupService.get(routeGroupCode);
        }
        var modeGroup = mode == null || mode.getSpec() == null ? null : mode.getSpec().getRouteGroup();
        if (StringUtils.hasText(modeGroup)) {
            return routeGroupService.get(modeGroup);
        }
        return routeGroupService
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
        return RouteRequest.builder()
                .routeMode(spec.getRouteMode())
                .requestedTier(input.requestedTier())
                .chatModeDefaultTier(modeTier)
                .routeGroupDefaultTier(spec.getDefaultTier())
                .affinity(spec.getAffinity())
                .previousSelection(input.previousSelection())
                .promptTokens(AiTokenEstimator.estimateMessages(input.ir().messages()))
                .maxOutputTokens(maxOutput)
                .requiredCapabilities(capabilities(input.ir()))
                .fallbackReferences(spec.getFallback())
                .manualPick(spec.getManualPick())
                .healthThreshold(properties.getRouting().getHealthThreshold())
                .qualityThreshold(spec.getQualityThreshold() == null ? 0.6 : spec.getQualityThreshold())
                .classifierEnabled(routing.isClassifierEnabled())
                .classifierFallbackTier(routing.getClassifierFallbackTier())
                .lastUserMessage(lastUserText(input.ir()))
                .build();
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
        return capabilities;
    }

    private static boolean hasImage(AiChatIr ir) {
        return ir.messages().stream()
                .flatMap(message -> message.parts().stream())
                .anyMatch(AiContentPart.Image.class::isInstance);
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
            @Nullable RouteSelection previousSelection) {}

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
            List<RouteSeat> seats) {

        public RouteSelection selection() {
            return plan.selected();
        }
    }
}
