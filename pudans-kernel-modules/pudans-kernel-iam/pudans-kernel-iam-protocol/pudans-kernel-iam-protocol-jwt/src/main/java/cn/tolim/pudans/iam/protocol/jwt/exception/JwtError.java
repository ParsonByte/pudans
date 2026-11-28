
package cn.tolim.pudans.iam.protocol.jwt.exception;

import org.springframework.security.core.SpringSecurityCoreVersion;
import org.springframework.util.Assert;

import java.io.Serial;
import java.io.Serializable;


public class JwtError implements Serializable {

    @Serial
    private static final long serialVersionUID = SpringSecurityCoreVersion.SERIAL_VERSION_UID;

    private final String errorCode;

    private final String description;

    private final String uri;


    public JwtError(String errorCode) {
        this(errorCode, null);
    }


    public JwtError(String errorCode, String description) {
        this(errorCode, description, null);
    }


    public JwtError(String errorCode, String description, String uri) {
        Assert.hasText(errorCode, "errorCode 不能为空");
        this.errorCode = errorCode;
        this.description = description;
        this.uri = uri;
    }


    public final String getErrorCode() {
        return this.errorCode;
    }


    public final String getDescription() {
        return this.description;
    }


    public String getUri() {
        return uri;
    }

    @Override
    public String toString() {
        return "[" + this.getErrorCode() + "] "
                + ((this.getDescription() != null) ? this.getDescription() : "");
    }

}
