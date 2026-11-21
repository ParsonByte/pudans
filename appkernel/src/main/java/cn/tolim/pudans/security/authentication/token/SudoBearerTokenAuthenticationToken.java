package cn.tolim.pudans.security.authentication.token;

import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

final class SudoBearerTokenAuthenticationToken extends BearerTokenAuthenticationToken {

    private final String sudoUsername;

    // 保存原始令牌与代操作用户名供认证流程使用。
    SudoBearerTokenAuthenticationToken(String token, String sudoUsername) {
        super(token);
        this.sudoUsername = sudoUsername;
    }

    // 供认证管理器确定代操作的目标用户。
    String getSudoUsername() {
        return sudoUsername;
    }
}
