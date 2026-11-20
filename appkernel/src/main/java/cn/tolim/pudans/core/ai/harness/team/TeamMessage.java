package cn.tolim.pudans.core.ai.harness.team;

import java.time.Instant;
import java.util.Objects;

public record TeamMessage(long id, String from, String to, String content, Instant sentAt) {

    public TeamMessage {
        from = Objects.requireNonNull(from, "发送人不能为空");
        to = Objects.requireNonNull(to, "接收人不能为空");
        content = content == null ? "" : content;
        sentAt = Objects.requireNonNull(sentAt, "发送时间不能为空");
    }
}