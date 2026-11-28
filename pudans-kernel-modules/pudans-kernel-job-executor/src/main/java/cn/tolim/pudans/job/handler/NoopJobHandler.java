package cn.tolim.pudans.job.handler;

import cn.tolim.pudans.job.support.JobConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

// Built-in handler that does nothing, useful for smoke tests and scheduling checks
@Component
public class NoopJobHandler implements JobHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NoopJobHandler.class);

    private final AtomicLong executions = new AtomicLong();

    @Override
    public String name() {
        return JobConstants.NOOP_HANDLER;
    }

    @Override
    public String description() {
        return "Built-in no-op handler";
    }

    @Override
    public JobHandlerResult execute(JobExecutionContext context) {
        long count = executions.incrementAndGet();
        LOGGER.debug("No-op job {} executed, total executions {}", context.jobCode(), count);
        return JobHandlerResult.ok("no-op executed");
    }
}