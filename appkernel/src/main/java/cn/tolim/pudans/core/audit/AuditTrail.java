package cn.tolim.pudans.core.audit;

import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;


@Getter
@RequiredArgsConstructor
public class AuditTrail {

    private final String traceFlowId;

    private final String rootAuditId;

    private final AtomicInteger stepCounter = new AtomicInteger(1);

    // 返回下一个步骤序号，供子记录排序使用。
    public int nextStep() {
        return stepCounter.incrementAndGet();
    }
}
