package cn.tolim.pudans.core.ai.config;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RouteGroups {

    private final ReactiveExtensionClient client;

    private final AiSettings aiSettings;

    public Mono<AiRouteGroup> get(String code) {
        return client.get(AiRouteGroup.class, code);
    }

    public Mono<AiRouteGroup> defaultGroup() {
        return aiSettings.spec().flatMap(spec -> {
            if (spec != null && StringUtils.hasText(spec.getDefaultRouteGroup())) {
                return get(spec.getDefaultRouteGroup());
            }
            var options = ListOptions.builder()
                    .andQuery(equal("spec.isDefault", true))
                    .andQuery(equal("spec.enabled", true))
                    .build();
            return client.listAll(AiRouteGroup.class, options, defaultSort())
                    .next()
                    .switchIfEmpty(Mono.error(new IllegalStateException("未配置默认路由组。")));
        });
    }
}
