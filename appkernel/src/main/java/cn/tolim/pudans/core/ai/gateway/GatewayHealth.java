package cn.tolim.pudans.core.ai.gateway;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.SystemVersionSupplier;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.infra.properties.AiProperties;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiChatRequestRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class GatewayHealth {

    private static final Duration RECENT_WINDOW = Duration.ofHours(24);

    private static final List<AiProvider.Protocol> DEFAULT_PROTOCOLS =
            List.of(AiProvider.Protocol.OPENAI_CHAT, AiProvider.Protocol.ANTHROPIC);

    private final ReactiveExtensionClient client;

    private final AiSettings aiSettings;

    private final AiChatRequestRepository chatRequestRepository;

    private final AiProperties properties;

    private final SystemVersionSupplier versionSupplier;

    public Mono<Status> status() {
        return Mono.zip(
                        aiSettings.spec().defaultIfEmpty(new AiSetting.Spec()),
                        routeGroups(),
                        models(),
                        chatRequestRepository
                                .countRecentFailures(Instant.now().minus(RECENT_WINDOW))
                                .defaultIfEmpty(0L))
                .map(tuple -> {
                    var setting = tuple.getT1();
                    var gateway = setting.getGateway() == null ? new AiSetting.Gateway() : setting.getGateway();
                    var groups = tuple.getT2();
                    var defaultGroup = StringUtils.hasText(setting.getDefaultRouteGroup())
                            ? setting.getDefaultRouteGroup()
                            : groups.defaultName();
                    var version = versionSupplier.get();
                    return new Status(
                            properties.isEnabled()
                                    && properties.getGateway() != null
                                    && properties.getGateway().isEnabled(),
                            properties.getRuntime() == null ? null : properties.getRuntime().getGatewayUrl(),
                            protocols(gateway),
                            tuple.getT3(),
                            groups.names(),
                            defaultGroup,
                            gateway.isRateLimitEnabled(),
                            tuple.getT4(),
                            version == null ? null : version.toString());
                });
    }

    private static List<AiProvider.Protocol> protocols(AiSetting.Gateway gateway) {
        var supported = gateway.getSupportedProtocols();
        return supported == null || supported.isEmpty() ? DEFAULT_PROTOCOLS : List.copyOf(supported);
    }

    private Mono<GroupList> routeGroups() {
        var options = ListOptions.builder().andQuery(equal("spec.enabled", true)).build();
        return client.listAll(AiRouteGroup.class, options, defaultSort())
                .filter(group -> group.getMetadata() != null && group.getMetadata().getName() != null)
                .collectList()
                .map(groups -> {
                    var names = new ArrayList<String>();
                    String defaultName = null;
                    for (var group : groups) {
                        var name = group.getMetadata().getName();
                        names.add(name);
                        if (defaultName == null && group.getSpec() != null && group.getSpec().isDefault()) {
                            defaultName = name;
                        }
                    }
                    return new GroupList(List.copyOf(names), defaultName);
                });
    }

    private Mono<List<String>> models() {
        var options = ListOptions.builder().andQuery(equal("spec.enabled", true)).build();
        return client.listAll(AiModel.class, options, defaultSort())
                .filter(model -> model.getSpec() != null
                        && StringUtils.hasText(model.getSpec().getModelCode()))
                .map(model -> model.getSpec().getModelCode())
                .distinct()
                .sort()
                .collectList();
    }

    public record Status(
            boolean enabled,
            @Nullable String entryUrl,
            List<AiProvider.Protocol> protocols,
            List<String> models,
            List<String> routeGroups,
            @Nullable String defaultRouteGroup,
            boolean rateLimitEnabled,
            long recentErrors,
            @Nullable String version) {

        public Status {
            protocols = protocols == null ? List.of() : List.copyOf(protocols);
            models = models == null ? List.of() : List.copyOf(models);
            routeGroups = routeGroups == null ? List.of() : List.copyOf(routeGroups);
        }
    }

    private record GroupList(List<String> names, @Nullable String defaultName) {}
}