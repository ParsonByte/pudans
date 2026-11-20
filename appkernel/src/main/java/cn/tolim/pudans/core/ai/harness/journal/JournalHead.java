package cn.tolim.pudans.core.ai.harness.journal;

public record JournalHead(long seq) {

    public JournalHead {
        if (seq < 0) {
            throw new JournalException("线程日志序号不能为负数。");
        }
    }

    public static JournalHead empty() {
        return new JournalHead(0);
    }
}