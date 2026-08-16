package cn.tolim.pudans.contract.core.endpoint;

import cn.tolim.pudans.contract.extension.GroupVersion;
import org.springframework.web.reactive.socket.WebSocketHandler;


public interface WebSocketEndpoint {


    String urlPath();


    GroupVersion groupVersion();


    WebSocketHandler handler();
}
