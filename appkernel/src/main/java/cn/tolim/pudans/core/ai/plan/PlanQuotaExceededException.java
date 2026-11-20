package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Instant;
import org.springframework.http.HttpStatus;

public class PlanQuotaExceededException extends ApiException {

    private final PlanWindowKind windowKind;

    private final long remaining;

    private final Instant resetsAt;

    public PlanQuotaExceededException(PlanWindowKind windowKind, long remaining, Instant resetsAt) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                PlanQuotaService.CODE_QUOTA_EXCEEDED,
                label(windowKind) + "窗口额度不足：剩余 " + remaining + "，重置时间 " + resetsAt + "。");
        this.windowKind = windowKind;
        this.remaining = remaining;
        this.resetsAt = resetsAt;
    }

    public PlanWindowKind windowKind() {
        return windowKind;
    }

    public long remaining() {
        return remaining;
    }

    public Instant resetsAt() {
        return resetsAt;
    }

    private static String label(PlanWindowKind kind) {
        return kind == PlanWindowKind.FIVE_HOUR ? "5 小时" : "周";
    }
}