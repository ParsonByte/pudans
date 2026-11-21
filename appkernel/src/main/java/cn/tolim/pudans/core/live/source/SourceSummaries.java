package cn.tolim.pudans.core.live.source;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;

import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveSourceSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// 直播源看板汇总，状态计数来自内存注册表而不是全表扫描
@Component
@RequiredArgsConstructor
public class SourceSummaries {

    private final ReactiveExtensionClient client;

    private final LiveSourceRegistry registry;

    // 总数查库，状态计数直接取内存快照
    public Mono<LiveSourceSummary> summary() {
        var options = ListOptions.builder().andQuery(notDeleting()).build();
        return client.countBy(LiveSource.class, options).map(total -> {
            var counts = registry.counts();
            return new LiveSourceSummary(
                    total,
                    counts.enabled(),
                    counts.live(),
                    counts.recording(),
                    counts.offline(),
                    counts.error(),
                    counts.rateLimited());
        });
    }
}
