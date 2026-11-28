package cn.tolim.pudans.iam.synchronizer.task;

import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.infra.trace.TraceUtils;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.StopWatch;

import java.util.concurrent.TimeUnit;

import static cn.tolim.pudans.iam.infra.constant.IamConstants.CACHE_LOCK_KEY_PREFIX;
import static cn.tolim.pudans.iam.infra.constant.IamConstants.COLON;


@RequiredArgsConstructor
public class IdentitySourceSyncTask implements Runnable {
    private static final Logger logger = LoggerFactory
            .getLogger(IdentitySourceSyncTask.class);
    private final AlternativeJdkIdGenerator idGenerator = new AlternativeJdkIdGenerator();
    // ID
    private final String id;
    // Name
    private final String name;
    // RedissonClient
    private final RedissonClient redissonClient;
    // Identity source
    private final IdentitySource<? extends IdentitySourceConfig> identitySource;

    // When an object implementing interface Runnable is used
    // to create a thread, starting the thread causes the object's
    // run method to be called in that separately executing
    // thread.
    // The general contract of the method run is that it may
    // take any action whatsoever.
    @Override
    public void run() {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        logger.info("[任务触发]-同步身份源[{}]数据开始", name);
        String traceId = idGenerator.generateId().toString();
        TraceUtils.put(traceId);
        RLock lock = redissonClient.getLock(CACHE_LOCK_KEY_PREFIX + COLON + id);
        boolean tryLock = false;
        try {
            tryLock = lock.tryLock(1, TimeUnit.SECONDS);
            if (tryLock) {
                identitySource.sync(TriggerType.JOB);
            }
        } catch (InterruptedException ignored) {

        } finally {
            stopWatch.stop();
            if (tryLock && lock.isLocked()) {
                lock.unlock();
            }
            TraceUtils.remove();
            logger.info("[任务触发]-同步身份源[{}]数据结束, 执行时长: {} ms", name, stopWatch.getTotalTimeMillis());
        }
    }

}
