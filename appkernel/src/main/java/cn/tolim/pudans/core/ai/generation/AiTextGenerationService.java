package cn.tolim.pudans.core.ai.generation;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.config.AiRouteGroupService;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiTokenEstimator;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChatService;
import cn.tolim.pudans.core.ai.routing.RouteCandidateService;
import cn.tolim.pudans.core.ai.routing.RouteEngine;
import cn.tolim.pudans.core.ai.routing.RouteRequest;
import cn.tolim.pudans.core.ai.routing.RouteSeat;
import cn.tolim.pudans.core.ai.routing.RouteSelection;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.AiProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiTextGenerationService {

    private static final int DEFAULT_MAX_OUTPUT_TOKENS = 1024;

    private static final double QUALITY_THRESHOLD = 0.6;

    private static final String CODE_NO_CANDIDATE = "ai-route-no-candidate";

    private static final String CODE_GENERATION_FAILED = "ai-generation-failed";

    private final AiRouteGroupService routeGroupService;

    private final RouteCandidateService candidateService;

    private final RouteEngine routeEngine;

    private final LangChain4jChatService chatService;

    private final ReactiveExtensionClient client;

    private final AiProperties properties;

    public Mono<GenerationResult> generate(GenerationRequest request) {
        Objects.requireNonNull(request, "request");
        var maxTokens = request.maxOutputTokens() <= 0 ? DEFAULT_MAX_OUTPUT_TOKENS : request.maxOutputTokens();
        var ir = chatIr(request, maxTokens);
        return group(request.routeGroupCode())
                .flatMap(group -> candidateService
                        .expand(group)
                        .onErrorMap(AiTextGenerationService::noCandidateError)
                        .flatMap(seats -> route(group, seats, ir, maxTokens)));
    }

    private Mono<AiRouteGroup> group(@Nullable String routeGroupCode) {
        var source = StringUtils.hasText(routeGroupCode)
                ? routeGroupService.get(routeGroupCode)
                : routeGroupService.defaultGroup();
        return source.onErrorMap(AiTextGenerationService::noCandidateError)
                .filter(group -> group.getSpec() != null && group.getSpec().isEnabled())
                .switchIfEmpty(Mono.error(noCandidateError(null)));
    }

    private Mono<GenerationResult> route(
            AiRouteGroup group, List<RouteSeat> seats, AiChatIr ir, int maxTokens) {
        var plan = routeEngine.route(routeRequest(group, ir, maxTokens), seats);
        if (!plan.routed()) {
            return Mono.error(noCandidateError(null));
        }
        var selections = new ArrayList<RouteSelection>(1 + plan.fallbackSeats().size());
        selections.add(plan.selected());
        selections.addAll(plan.fallbackSeats());
        return attempt(List.copyOf(selections), 0, ir, null);
    }

    private Mono<GenerationResult> attempt(
            List<RouteSelection> selections, int index, AiChatIr ir, @Nullable Throwable lastError) {
        if (index >= selections.size()) {
            return Mono.error(generationFailed(lastError));
        }
        var selection = selections.get(index);
        return load(selection, ir)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "AI 路由资源不存在: " + selection.reference())))
                .flatMap(resources -> chatService
                        .chat(
                                resources.provider(),
                                resources.account(),
                                resources.model(),
                                resources.provider().getSpec().getProtocol(),
                                resources.ir())
                        .switchIfEmpty(Mono.error(new IllegalStateException("AI 供应商返回空响应。")))
                        .map(response -> result(response, selection, resources.model())))
                .onErrorResume(error -> attempt(selections, index + 1, ir, error));
    }

    private Mono<Resources> load(RouteSelection selection, AiChatIr ir) {
        return client.get(AiProvider.class, selection.provider())
                .flatMap(provider -> client.get(AiProviderAccount.class, selection.account())
                        .flatMap(account -> client.get(AiModel.class, selection.model())
                                .map(model -> new Resources(provider, account, model, ir))));
    }

    private RouteRequest routeRequest(AiRouteGroup group, AiChatIr ir, int maxTokens) {
        var spec = group.getSpec();
        return RouteRequest.builder()
                .routeMode(spec.getRouteMode())
                .affinity(spec.getAffinity())
                .requestedTier(null)
                .routeGroupDefaultTier(spec.getDefaultTier())
                .promptTokens(AiTokenEstimator.estimateMessages(ir.messages()))
                .maxOutputTokens(maxTokens)
                .manualPick(spec.getManualPick())
                .healthThreshold(properties.getRouting().getHealthThreshold())
                .qualityThreshold(QUALITY_THRESHOLD)
                .classifierEnabled(false)
                .build();
    }

    private static AiChatIr chatIr(GenerationRequest request, int maxTokens) {
        var messages = List.of(
                new AiChatMessage("system", List.of(new AiContentPart.Text(request.systemPrompt(), null))),
                new AiChatMessage("user", List.of(new AiContentPart.Text(request.userPrompt(), null))));
        return new AiChatIr(
                messages,
                List.of(),
                new AiRequestParams(null, request.temperature(), maxTokens, null, null, null, null, null),
                false);
    }

    private static GenerationResult result(AiChatResponse response, RouteSelection selection, AiModel model) {
        var content = response.message() == null ? "" : text(response.message());
        var modelCode = model.getSpec() == null ? null : model.getSpec().getModelCode();
        return new GenerationResult(
                content,
                StringUtils.hasText(modelCode) ? modelCode : selection.model(),
                selection.provider(),
                selection.account());
    }

    private static String text(AiChatMessage message) {
        return message.parts().stream()
                .filter(AiContentPart.Text.class::isInstance)
                .map(AiContentPart.Text.class::cast)
                .map(AiContentPart.Text::text)
                .collect(Collectors.joining());
    }

    private static ApiException noCandidateError(@Nullable Throwable cause) {
        var message = "没有可用的 AI 路由候选。";
        return cause == null
                ? new ApiException(HttpStatus.SERVICE_UNAVAILABLE, CODE_NO_CANDIDATE, message)
                : new ApiException(HttpStatus.SERVICE_UNAVAILABLE, CODE_NO_CANDIDATE, message, cause);
    }

    private static ApiException generationFailed(@Nullable Throwable cause) {
        var message = "AI 文本生成失败。";
        return cause == null
                ? new ApiException(HttpStatus.BAD_GATEWAY, CODE_GENERATION_FAILED, message)
                : new ApiException(HttpStatus.BAD_GATEWAY, CODE_GENERATION_FAILED, message, cause);
    }

    private record Resources(AiProvider provider, AiProviderAccount account, AiModel model, AiChatIr ir) {}
}
