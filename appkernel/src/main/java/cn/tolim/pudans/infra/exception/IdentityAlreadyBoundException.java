package cn.tolim.pudans.infra.exception;

import cn.tolim.pudans.contract.user.UserConnection;
import org.springframework.web.server.ServerWebInputException;


public class IdentityAlreadyBoundException extends ServerWebInputException {

    public IdentityAlreadyBoundException(UserConnection connection) {
        super("身份 " + connection.getSpec().getRegistrationId() + ":" + connection.getSpec().getProviderUserId()
                + " 已绑定到用户 " + connection.getSpec().getUsername() + "。");
    }
}
