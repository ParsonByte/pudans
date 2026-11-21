package cn.tolim.pudans.core.infra.exception;

import cn.tolim.pudans.contract.user.UserConnection;
import org.springframework.web.server.ServerWebInputException;


public class OAuth2UserAlreadyBoundException extends ServerWebInputException {

    public OAuth2UserAlreadyBoundException(UserConnection connection) {
        super("用户 " + connection.getSpec().getUsername()
                + " 已经绑定到另一个 OAuth2 用户，无法自动绑定当前 OAuth2 用户。");
    }
}
