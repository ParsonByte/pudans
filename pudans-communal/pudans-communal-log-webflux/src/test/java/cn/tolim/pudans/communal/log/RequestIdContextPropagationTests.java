package cn.tolim.pudans.communal.log;

import io.micrometer.context.ContextRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdContextPropagationTests {

    @BeforeAll
    static void enableReactorContextPropagation() {
        new PudansWebFluxLoggingAutoConfiguration();
        Hooks.enableAutomaticContextPropagation();
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void registersRequestIdAccessor() {
        assertThat(ContextRegistry.getInstance().getThreadLocalAccessors())
                .anyMatch(accessor -> MdcKeys.REQUEST_ID.equals(accessor.key()));
    }

    @Test
    void writesAndResetsRequestIdInMdc() {
        PudansWebFluxLoggingAutoConfiguration.RequestIdThreadLocalAccessor accessor =
                new PudansWebFluxLoggingAutoConfiguration.RequestIdThreadLocalAccessor();

        accessor.setValue("req-42");

        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isEqualTo("req-42");
        accessor.setValue();
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
    }

    @Test
    void propagatesRequestIdToMdcAcrossThreadSwitch() {
        AtomicReference<String> observed = new AtomicReference<>();

        Mono.deferContextual(context -> Mono.fromCallable(() -> {
                    observed.set(MDC.get(MdcKeys.REQUEST_ID));
                    return context.get(MdcKeys.REQUEST_ID);
                })
                .subscribeOn(Schedulers.boundedElastic()))
                .contextWrite(context -> context.put(MdcKeys.REQUEST_ID, "req-42"))
                .block(Duration.ofSeconds(5));

        assertThat(observed.get()).isEqualTo("req-42");
    }
}