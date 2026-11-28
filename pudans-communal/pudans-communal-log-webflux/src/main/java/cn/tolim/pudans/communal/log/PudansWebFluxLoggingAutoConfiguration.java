package cn.tolim.pudans.communal.log;

import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import io.micrometer.tracing.Tracer;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.server.WebFilter;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(WebFilter.class)
@ConditionalOnProperty(prefix = "pudans.log", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PudansLogProperties.class)
public class PudansWebFluxLoggingAutoConfiguration {

    public PudansWebFluxLoggingAutoConfiguration() {
        // Registers requestId so Reactor restores the MDC after operator thread switches
        ContextRegistry registry = ContextRegistry.getInstance();
        boolean registered = registry.getThreadLocalAccessors().stream()
                .anyMatch(accessor -> MdcKeys.REQUEST_ID.equals(accessor.key()));
        if (!registered) {
            registry.registerThreadLocalAccessor(new RequestIdThreadLocalAccessor());
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceWebFilter pudansTraceWebFilter(PudansLogProperties properties, ObjectProvider<Tracer> tracerProvider) {
        return new TraceWebFilter(properties, tracerProvider);
    }

    // Bridges the reactor context requestId entry to the SLF4J MDC
    static final class RequestIdThreadLocalAccessor implements ThreadLocalAccessor<String> {

        @Override
        public Object key() {
            return MdcKeys.REQUEST_ID;
        }

        @Override
        public String getValue() {
            return MDC.get(MdcKeys.REQUEST_ID);
        }

        @Override
        public void setValue(String value) {
            if (value == null) {
                MDC.remove(MdcKeys.REQUEST_ID);
            } else {
                MDC.put(MdcKeys.REQUEST_ID, value);
            }
        }

        @Override
        public void setValue() {
            MDC.remove(MdcKeys.REQUEST_ID);
        }
    }
}