package cn.tolim.pudans.core.ai.harness.journal;

// 序号冲突：说明另一个追加已推进 head，可按新序号重试。
public class JournalConflictException extends JournalException {

    public JournalConflictException(String message) {
        super(message);
    }
}
