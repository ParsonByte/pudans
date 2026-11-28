
package cn.tolim.pudans.iam.protocol.jwt.exception;

import lombok.Getter;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.Assert;


@Getter
public class JwtAuthenticationException extends AuthenticationException {

    private final JwtError error;

    public JwtAuthenticationException(JwtError error) {
        super(error.getDescription());
        this.error = error;
    }

    public JwtAuthenticationException(JwtError error, Throwable cause) {
        this(error, error.getDescription(), cause);
    }

    public JwtAuthenticationException(JwtError error, String message, Throwable cause) {
        super(message, cause);
        Assert.notNull(error, "error 不能为空");
        this.error = error;
    }
}
