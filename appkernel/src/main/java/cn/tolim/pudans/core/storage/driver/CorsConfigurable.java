package cn.tolim.pudans.core.storage.driver;

import java.util.List;
import reactor.core.publisher.Mono;

public interface CorsConfigurable {

    Mono<Void> configureCors(List<String> origins);
}
