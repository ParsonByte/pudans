package cn.tolim.pudans.infra;

import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.support.ServerRequestWrapper;
import org.springframework.web.server.ServerWebExchange;


public class SecureServerRequest extends ServerRequestWrapper {


    public SecureServerRequest(ServerRequest delegate) {
        super(delegate);
    }

    @Override
    public ServerWebExchange exchange() {
        return new SecureServerWebExchange(super.exchange());
    }
}
