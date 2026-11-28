package cn.tolim.pudans.iam.identitysource.core.event;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.listener.MessageListener;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentitySourceEventListenerRunnerTests {

    @SuppressWarnings("unchecked")
    private static MessageListener<IdentitySourceEvent> captureListener(
            RedissonClient redissonClient, RTopic topic) {
        ArgumentCaptor<MessageListener<IdentitySourceEvent>> captor =
                ArgumentCaptor.forClass(MessageListener.class);
        verify(topic).addListener(eq(IdentitySourceEvent.class), captor.capture());
        return captor.getValue();
    }

    @Test
    void shouldDispatchRegisterDestroyAndSyncEvents() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        IdentitySourceEventListener listener = mock(IdentitySourceEventListener.class);
        when(redissonClient.getTopic(IdentitySourceEventUtils.IDENTITY_SOURCE_EVENT))
                .thenReturn(topic);
        when(topic.addListener(eq(IdentitySourceEvent.class), any())).thenReturn(1);
        IdentitySourceEventListenerRunner runner =
                new IdentitySourceEventListenerRunner(redissonClient, listener);

        runner.run();

        MessageListener<IdentitySourceEvent> eventListener = captureListener(redissonClient, topic);
        eventListener.onMessage("channel",
                new IdentitySourceEvent("source-1", IdentitySourceEventType.REGISTER));
        eventListener.onMessage("channel",
                new IdentitySourceEvent("source-1", IdentitySourceEventType.DESTROY));
        eventListener.onMessage("channel",
                new IdentitySourceEvent("source-1", IdentitySourceEventType.SYNC));

        verify(listener).register("source-1");
        verify(listener).destroy("source-1");
        verify(listener).sync("source-1");
    }

    @Test
    void shouldRunAtHighestPrecedenceAndExposeApplicationContext() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        IdentitySourceEventListener listener = mock(IdentitySourceEventListener.class);
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        IdentitySourceEventListenerRunner runner =
                new IdentitySourceEventListenerRunner(redissonClient, listener);

        runner.setApplicationContext(applicationContext);

        assertEquals(Ordered.HIGHEST_PRECEDENCE, runner.getOrder());
        assertSame(applicationContext, runner.getApplicationContext());
    }
}