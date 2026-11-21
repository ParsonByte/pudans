package cn.tolim.pudans.infra;

import org.springframework.context.ApplicationContext;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebExchangeDecorator;


public class SecureServerWebExchange extends ServerWebExchangeDecorator {

    public SecureServerWebExchange(ServerWebExchange delegate) {
        super(delegate);
    }

    @Override
    public ApplicationContext getApplicationContext() {

        return null;
    }
}
