package cn.tolim.pudans.core.storage.driver;

import java.util.List;
import reactor.core.publisher.Mono;

public interface CorsConfigurable {

    // 设置存储桶允许的跨域来源。
    Mono<Void> configureCors(List<String> origins);
}
