package cn.tolim.pudans.core.live.recorder;

import java.time.Instant;

// 已关闭分片的本地元数据，按索引升序写入 segments.jsonl
public record LiveSegment(
        int index, String filename, Instant startedAt, Instant endedAt, long durationMillis, long size) {}
