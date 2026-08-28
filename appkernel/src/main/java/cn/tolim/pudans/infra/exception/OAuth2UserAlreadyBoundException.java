package cn.tolim.pudans.infra.exception;

import cn.tolim.pudans.contract.core.extension.user.UserConnection;
import org.springframework.web.server.ServerWebInputException;


public class OAuth2UserAlreadyBoundException extends ServerWebInputException {

    public OAuth2UserAlreadyBoundException(UserConnection connection) {
        super("The user has been bound to another account", null, null, null, new Object[] {
                connection.getSpec().getUsername(),
                connection.getSpec().getProviderUserId(),
                connection.getSpec().getRegistrationId(),
                connection.getSpec().getUpdatedAt()
        });
    }
}
