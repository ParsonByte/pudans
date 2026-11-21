package cn.tolim.pudans.infra.contract;

import java.util.function.Supplier;
import reactor.core.publisher.Mono;

public interface ParameterInfoGetter extends Supplier<Mono<SystemInfo>> {}
