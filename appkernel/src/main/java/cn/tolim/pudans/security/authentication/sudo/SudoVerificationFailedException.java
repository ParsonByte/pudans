package cn.tolim.pudans.security.authentication.sudo;

import org.springframework.web.server.ServerWebInputException;


public class SudoVerificationFailedException extends ServerWebInputException {

    public SudoVerificationFailedException() {
        super("Verification failed", null, null, "problemDetail.sudo.verificationFailed", null);
    }
}
