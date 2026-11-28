package cn.tolim.pudans.iam.identitysource.core.event;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeansException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.lang.NonNull;


@Slf4j
@RequiredArgsConstructor
@Configuration
@ConditionalOnBean(value = IdentitySourceEventListener.class)
public class IdentitySourceEventListenerRunner implements CommandLineRunner, Ordered,
        ApplicationContextAware {

    private final RedissonClient redissonClient;
    private final IdentitySourceEventListener identitySourceEventListener;
    @Getter
    private ApplicationContext applicationContext;

    // Callback used to run the bean.
    // Parameter args incoming main method arguments
    @Override
    public void run(String... args) {
        RTopic topic = redissonClient.getTopic(IdentitySourceEventUtils.IDENTITY_SOURCE_EVENT);
        // Add listener
        topic.addListener(IdentitySourceEvent.class, (channel, msg) -> {
            // Register
            if (IdentitySourceEventType.REGISTER.equals(msg.getIdentitySourceEventType())) {
                identitySourceEventListener.register(msg.getId());
                return;
            }
            // Uninstall
            if (IdentitySourceEventType.DESTROY.equals(msg.getIdentitySourceEventType())) {
                identitySourceEventListener.destroy(msg.getId());
            }
            // Sync
            if (IdentitySourceEventType.SYNC.equals(msg.getIdentitySourceEventType())) {
                identitySourceEventListener.sync(msg.getId());
            }
        });
    }

    // Get the order value of this object.
    // Higher values are interpreted as lower priority. As a consequence,
    // the object with the lowest value has the highest priority (somewhat
    // analogous to Servlet load-on-startup values).
    // Same order values will result in arbitrary sort positions for the
    // affected objects.
    // Returns the order value
    // See #HIGHEST_PRECEDENCE
    // See #LOWEST_PRECEDENCE
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    // Set the ApplicationContext that this object runs in.
    // Normally this call will be used to initialize the object.
    // Invoked after population of normal bean properties but before an init callback such
    // as InitializingBean#afterPropertiesSet()
    // or a custom init-method. Invoked after ResourceLoaderAware#setResourceLoader and
    // ApplicationEventPublisherAware#setApplicationEventPublisher.
    // Parameter applicationContext the ApplicationContext object to be used by this object
    // Throws ApplicationContextException in case of context initialization errors
    // Throws BeansException if thrown by application context methods
    // See BeanInitializationException
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

}
