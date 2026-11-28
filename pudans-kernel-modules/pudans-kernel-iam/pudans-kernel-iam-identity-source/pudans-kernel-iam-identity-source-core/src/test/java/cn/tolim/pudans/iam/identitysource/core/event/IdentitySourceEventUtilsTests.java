package cn.tolim.pudans.iam.identitysource.core.event;

import cn.tolim.pudans.iam.infra.context.ApplicationContextService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentitySourceEventUtilsTests {

    private static void assertPublished(MockedStatic<ApplicationContextService> context,
                                        RTopic topic,
                                        IdentitySourceEventType expectedType) {
        ArgumentCaptor<IdentitySourceEvent> captor =
                ArgumentCaptor.forClass(IdentitySourceEvent.class);
        verify(topic).publish(captor.capture());
        assertEquals("source-1", captor.getValue().getId());
        assertEquals(expectedType, captor.getValue().getIdentitySourceEventType());
        context.verify(() -> ApplicationContextService.getBean(RedissonClient.class));
    }

    @Test
    void shouldPublishRegisterEvent() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.getTopic(IdentitySourceEventUtils.IDENTITY_SOURCE_EVENT))
                .thenReturn(topic);
        try (MockedStatic<ApplicationContextService> context =
                     Mockito.mockStatic(ApplicationContextService.class)) {
            context.when(() -> ApplicationContextService.getBean(RedissonClient.class))
                    .thenReturn(redissonClient);

            IdentitySourceEventUtils.register("source-1");

            assertPublished(context, topic, IdentitySourceEventType.REGISTER);
        }
    }

    @Test
    void shouldPublishDestroyEvent() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.getTopic(IdentitySourceEventUtils.IDENTITY_SOURCE_EVENT))
                .thenReturn(topic);
        try (MockedStatic<ApplicationContextService> context =
                     Mockito.mockStatic(ApplicationContextService.class)) {
            context.when(() -> ApplicationContextService.getBean(RedissonClient.class))
                    .thenReturn(redissonClient);

            IdentitySourceEventUtils.destroy("source-1");

            assertPublished(context, topic, IdentitySourceEventType.DESTROY);
        }
    }

    @Test
    void shouldPublishSyncEvent() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RTopic topic = mock(RTopic.class);
        when(redissonClient.getTopic(IdentitySourceEventUtils.IDENTITY_SOURCE_EVENT))
                .thenReturn(topic);
        try (MockedStatic<ApplicationContextService> context =
                     Mockito.mockStatic(ApplicationContextService.class)) {
            context.when(() -> ApplicationContextService.getBean(RedissonClient.class))
                    .thenReturn(redissonClient);

            IdentitySourceEventUtils.sync("source-1");

            assertPublished(context, topic, IdentitySourceEventType.SYNC);
        }
    }
}