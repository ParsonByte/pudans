package cn.tolim.pudans.core.ai.routing;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiRouteGroup;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import org.jspecify.annotations.Nullable;

@Value
@Builder
public class RouteRequest {

    @Builder.Default
    AiRouteGroup.RouteMode routeMode = AiRouteGroup.RouteMode.SMART;

    // 客户端显式请求的模型名，用于规则条件匹配。
    String requestedModel;

    @Nullable
    String agentName;

    @Builder.Default
    int imageCount = 0;

    // 客户端强制选择的模型，路由只在匹配该模型的候选内选择。
    String explicitModel;

    @Builder.Default
    String contextMode = "SMALLEST";

    @Builder.Default
    int patienceSeconds = 60;

    @Builder.Default
    int firstTokenSeconds = 0;

    @Builder.Default
    boolean sink = false;

    @Builder.Default
    List<AiRouteGroup.Rule> rules = List.of();

    AiModel.Tier requestedTier;

    AiModel.Tier chatModeDefaultTier;

    AiModel.Tier routeGroupDefaultTier;

    @Builder.Default
    AiProvider.Affinity affinity = AiProvider.Affinity.AUTO;

    RouteSelection previousSelection;

    @Builder.Default
    boolean affinityCacheWorthKeeping = true;

    @Builder.Default
    int promptTokens = 0;

    @Builder.Default
    int maxOutputTokens = 0;

    @Builder.Default
    Set<AiModel.Capability> requiredCapabilities = Set.of();

    @Builder.Default
    Set<AiModel.Modality> requiredInputModalities = Set.of();

    @Builder.Default
    Set<AiModel.Modality> requiredOutputModalities = Set.of();

    @Builder.Default
    List<String> fallbackReferences = List.of();

    String manualPick;

    @Builder.Default
    double healthThreshold = 60;

    @Builder.Default
    double qualityThreshold = 0.6;

    @Builder.Default
    boolean classifierEnabled = true;

    @Builder.Default
    AiModel.Tier classifierFallbackTier = AiModel.Tier.HIGH;

    String lastUserMessage;
}
