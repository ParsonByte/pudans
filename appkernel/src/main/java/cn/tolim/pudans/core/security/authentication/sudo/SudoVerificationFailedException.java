package cn.tolim.pudans.core.security.authentication.sudo;

import org.springframework.web.server.ServerWebInputException;


public class SudoVerificationFailedException extends ServerWebInputException {

    public SudoVerificationFailedException() {
        super("验证失败。");
    }
}
