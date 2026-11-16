package cn.tolim.pudans.security.authentication.twofactor;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TwoFactorAuthRequiredException extends ResponseStatusException {

    private static final URI type = URI.create("https://pudans.tolim.cn/probs/2fa-required");

    public TwoFactorAuthRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "请先完成双重验证。");
        setType(type);
    }
}
