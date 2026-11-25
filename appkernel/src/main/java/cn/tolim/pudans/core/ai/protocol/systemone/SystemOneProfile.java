package cn.tolim.pudans.core.ai.protocol.systemone;

import cn.tolim.pudans.core.ai.protocol.AiProtocolProfile;
import java.util.Map;

public interface SystemOneProfile extends AiProtocolProfile {

    default String requestPath(String providerPath) {
        return providerPath;
    }

    default Map<String, String> headers(Map<String, String> providerHeaders) {
        return providerHeaders;
    }

    default void customizeBody(Map<String, Object> body) {}
}
