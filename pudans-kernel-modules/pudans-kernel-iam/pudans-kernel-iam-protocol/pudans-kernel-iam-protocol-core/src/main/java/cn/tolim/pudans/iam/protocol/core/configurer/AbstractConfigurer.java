package cn.tolim.pudans.iam.protocol.core.configurer;

import cn.tolim.pudans.iam.protocol.core.EndpointMatcher;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;


@SuppressWarnings("AlibabaClassNamingShouldBeCamel")
public abstract class AbstractConfigurer {
    private final ObjectPostProcessor<Object> objectPostProcessor;

    public AbstractConfigurer(ObjectPostProcessor<Object> objectPostProcessor) {
        this.objectPostProcessor = objectPostProcessor;
    }

    // init
    // Parameter httpSecurity HttpSecurity
    public abstract void init(HttpSecurity httpSecurity);

    // configure
    // Parameter httpSecurity HttpSecurity
    public abstract void configure(HttpSecurity httpSecurity);

    // Get request matcher
    // Returns EndpointMatcher
    public abstract EndpointMatcher getEndpointMatcher();

    public final <T> T postProcess(T object) {
        return (T) this.objectPostProcessor.postProcess(object);
    }

    public final ObjectPostProcessor<Object> getObjectPostProcessor() {
        return this.objectPostProcessor;
    }

}
