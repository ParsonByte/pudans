package cn.tolim.pudans.infra.contract.trace;

import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

public final class TraceContext {

    public static final String TRACE_FLOW_ID_CONTEXT_KEY = TraceContext.class.getName() + ".TRACE_FLOW_ID";

    private TraceContext() {}

    public static Mono<String> currentTraceFlowId() {
        return Mono.deferContextual(context -> Mono.justOrEmpty(resolve(context)));
    }

    public static String resolve(ContextView context) {
        return context.getOrDefault(TRACE_FLOW_ID_CONTEXT_KEY, null);
    }
}
