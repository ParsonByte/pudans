package cn.tolim.pudans.plugin.endpoint;

import cn.tolim.pudans.contract.core.endpoint.WebSocketEndpoint;
import java.util.Collection;


public interface WebSocketEndpointManager {

    void register(Collection<WebSocketEndpoint> endpoints);

    void unregister(Collection<WebSocketEndpoint> endpoints);
}
