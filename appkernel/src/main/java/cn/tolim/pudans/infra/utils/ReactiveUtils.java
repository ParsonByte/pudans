package cn.tolim.pudans.infra.utils;

import java.time.Duration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public enum ReactiveUtils {
    ;

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    public static boolean isReactiveType(Class<?> clazz) {
        return Mono.class.isAssignableFrom(clazz) || Flux.class.isAssignableFrom(clazz);
    }
}
