package cn.tolim.pudans.security.authentication.twofactor;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class TwoFactorAuthRequiredException extends ResponseStatusException {

    private static final URI type = URI.create("https://pudans.tolim.cn/probs/2fa-required");

    // 统一返回未授权响应，提示调用方先完成双重验证。
    public TwoFactorAuthRequiredException() {
        super(HttpStatus.UNAUTHORIZED, "请先完成双重验证。");
        setType(type);
    }
}
