package cn.tolim.pudans.iam.synchronizer.task;

import cn.tolim.pudans.iam.communal.enums.TriggerType;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.infra.constant.IamConstants;
import cn.tolim.pudans.iam.infra.trace.TraceUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentitySourceSyncTaskTests {

    private static final String LOCK_KEY = IamConstants.CACHE_LOCK_KEY_PREFIX
            + IamConstants.COLON + "source-1";

    @AfterEach
    void clearTrace() {
        TraceUtils.remove();
    }

    @SuppressWarnings("unchecked")
    private static IdentitySource<IdentitySourceConfig> identitySource() {
        return mock(IdentitySource.class);
    }

    @Test
    void shouldRunSyncWhenLockIsAcquired() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        IdentitySource<IdentitySourceConfig> identitySource = identitySource();
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isLocked()).thenReturn(true);

        new IdentitySourceSyncTask("source-1", "Source", redissonClient, identitySource).run();

        verify(identitySource).sync(TriggerType.JOB);
        verify(lock).unlock();
        assertNull(TraceUtils.get());
    }

    @Test
    void shouldSkipSyncWhenLockIsHeld() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        IdentitySource<IdentitySourceConfig> identitySource = identitySource();
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(false);

        new IdentitySourceSyncTask("source-1", "Source", redissonClient, identitySource).run();

        verify(identitySource, never()).sync(TriggerType.JOB);
        verify(lock, never()).unlock();
        assertNull(TraceUtils.get());
    }

    @Test
    void shouldNotFailWhenInterrupted() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        IdentitySource<IdentitySourceConfig> identitySource = identitySource();
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenThrow(new InterruptedException());

        new IdentitySourceSyncTask("source-1", "Source", redissonClient, identitySource).run();

        verify(identitySource, never()).sync(TriggerType.JOB);
        verify(lock, never()).unlock();
        assertNull(TraceUtils.get());
    }

    @Test
    void shouldNotUnlockWhenLockWasNotAcquired() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);
        IdentitySource<IdentitySourceConfig> identitySource = identitySource();
        when(redissonClient.getLock(LOCK_KEY)).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isLocked()).thenReturn(false);

        new IdentitySourceSyncTask("source-1", "Source", redissonClient, identitySource).run();

        verify(identitySource).sync(TriggerType.JOB);
        verify(lock, never()).unlock();
    }
}