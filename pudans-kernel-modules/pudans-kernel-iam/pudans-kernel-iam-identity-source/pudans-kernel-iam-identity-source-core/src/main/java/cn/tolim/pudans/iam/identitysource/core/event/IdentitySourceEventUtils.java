package cn.tolim.pudans.iam.identitysource.core.event;

import cn.tolim.pudans.iam.infra.context.ApplicationContextService;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;


public class IdentitySourceEventUtils {
    public static final String IDENTITY_SOURCE_EVENT = "iam-identity-source-event";

    // refresh
    // Use Redis pub/sub to refresh and reload cluster parameters
    public static void destroy(String id) {
        RedissonClient redisson = ApplicationContextService.getBean(RedissonClient.class);
        RTopic topic = redisson.getTopic(IDENTITY_SOURCE_EVENT);
        topic.publish(new IdentitySourceEvent(id, IdentitySourceEventType.DESTROY));
    }

    // refresh
    // Use Redis pub/sub to refresh and reload cluster parameters
    public static void register(String id) {
        RedissonClient redisson = ApplicationContextService.getBean(RedissonClient.class);
        RTopic topic = redisson.getTopic(IDENTITY_SOURCE_EVENT);
        topic.publish(new IdentitySourceEvent(id, IdentitySourceEventType.REGISTER));
    }

    // sync
    // Use Redis pub/sub to dispatch sync tasks
    public static void sync(String id) {
        RedissonClient redisson = ApplicationContextService.getBean(RedissonClient.class);
        RTopic topic = redisson.getTopic(IDENTITY_SOURCE_EVENT);
        topic.publish(new IdentitySourceEvent(id, IdentitySourceEventType.SYNC));
    }

}
