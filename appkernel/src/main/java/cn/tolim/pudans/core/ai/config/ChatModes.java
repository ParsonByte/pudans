package cn.tolim.pudans.core.ai.config;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.ai.AiChatMode;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ChatModes {

    private final ReactiveExtensionClient client;

    public Mono<AiChatMode> get(String code) {
        return client.get(AiChatMode.class, code);
    }

    public Flux<AiChatMode> available() {
        var options = ListOptions.builder().andQuery(equal("spec.enabled", true)).build();
        return client.listAll(AiChatMode.class, options, defaultSort());
    }

    public Mono<AiRouteGroup> routeGroup(String modeCode) {
        return get(modeCode).flatMap(mode -> {
            var routeGroup = mode.getSpec() == null ? null : mode.getSpec().getRouteGroup();
            if (!StringUtils.hasText(routeGroup)) {
                return Mono.error(new IllegalStateException("对话模式未绑定路由组：" + modeCode));
            }
            return client.get(AiRouteGroup.class, routeGroup);
        });
    }
}
