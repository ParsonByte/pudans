package cn.tolim.pudans.security.authentication.sudo;

import org.springframework.web.server.ServerWebInputException;


public class SudoVerificationFailedException extends ServerWebInputException {

    // 统一以输入错误返回验证失败，避免泄露细节
    public SudoVerificationFailedException() {
        super("验证失败。");
    }
}
