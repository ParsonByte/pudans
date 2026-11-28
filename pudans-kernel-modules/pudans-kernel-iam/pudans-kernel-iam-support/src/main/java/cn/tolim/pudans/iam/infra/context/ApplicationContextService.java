package cn.tolim.pudans.iam.infra.context;

import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ResolvableType;

public final class ApplicationContextService {
    private static ApplicationContext applicationContext;

    private ApplicationContextService() {
    }

    public static ApplicationContext getApplicationContext() {
        if (applicationContext == null) {
            throw new IllegalStateException("应用上下文未初始化");
        }
        return applicationContext;
    }

    public static void setApplicationContext(ApplicationContext context) {
        applicationContext = context;
    }

    public static <T> T getBean(Class<T> type) {
        return getApplicationContext().getBean(type);
    }

    public static Object getBean(String name) {
        return getApplicationContext().getBean(name);
    }

    public static <T> T getBean(String name, Class<T> type) {
        return getApplicationContext().getBean(name, type);
    }

    public static <T> ObjectProvider<T> getBeanProvider(ResolvableType type) {
        return getApplicationContext().getBeanProvider(type);
    }

    public static void refresh(String key) {
        getBean(RedissonClient.class).getTopic("pudans-config-refresh").publish(key);
    }
}
