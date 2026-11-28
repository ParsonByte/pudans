
package cn.tolim.pudans.iam.protocol.jwt.exception;

import static cn.tolim.pudans.iam.protocol.jwt.constant.JwtProtocolConstants.JWT_ERROR_URI;
import static cn.tolim.pudans.iam.protocol.jwt.exception.JwtErrorCodes.GENERATE_ID_TOKEN_ERROR;


public class IdTokenGenerateException extends JwtAuthenticationException {

    public IdTokenGenerateException() {
        super(new JwtError(GENERATE_ID_TOKEN_ERROR, null, JWT_ERROR_URI));
    }

    public IdTokenGenerateException(Throwable cause) {
        super(new JwtError(GENERATE_ID_TOKEN_ERROR, null, JWT_ERROR_URI), cause);
    }
}
