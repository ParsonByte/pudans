package cn.tolim.pudans.core.live.recording;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;
import static cn.tolim.pudans.contract.extension.index.query.Queries.greaterThan;
import static cn.tolim.pudans.contract.extension.index.query.Queries.in;
import static cn.tolim.pudans.contract.extension.index.query.Queries.lessThan;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.contract.extension.PageRequestImpl;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveRecordingQuery;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

// 录制记录分页查询：按源、状态与开始时间过滤
@Component
@RequiredArgsConstructor
public class RecordingQueries {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final ReactiveExtensionClient client;

    public Mono<ListResult<LiveRecording>> query(LiveRecordingQuery query) {
        var page = query == null || query.page() < 1 ? 1 : query.page();
        var requestedSize = query == null || query.size() < 1 ? DEFAULT_PAGE_SIZE : query.size();
        var size = Math.min(requestedSize, MAX_PAGE_SIZE);
        var builder = ListOptions.builder();
        if (query != null && StringUtils.hasText(query.sourceName())) {
            builder.andQuery(equal("spec.sourceName", query.sourceName()));
        }
        if (query != null && query.states() != null && !query.states().isEmpty()) {
            builder.andQuery(in("status.state", List.copyOf(query.states())));
        }
        if (query != null && query.from() != null) {
            builder.andQuery(greaterThan("spec.startedAt", query.from(), true));
        }
        if (query != null && query.to() != null) {
            builder.andQuery(lessThan("spec.startedAt", query.to(), true));
        }
        return client.listBy(LiveRecording.class, builder.build(), PageRequestImpl.of(page, size, defaultSort()));
    }
}
