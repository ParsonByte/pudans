package cn.tolim.pudans.core.security.authentication.token;

import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

final class SudoBearerTokenAuthenticationToken extends BearerTokenAuthenticationToken {

    private final String sudoUsername;

    SudoBearerTokenAuthenticationToken(String token, String sudoUsername) {
        super(token);
        this.sudoUsername = sudoUsername;
    }

    String getSudoUsername() {
        return sudoUsername;
    }
}
