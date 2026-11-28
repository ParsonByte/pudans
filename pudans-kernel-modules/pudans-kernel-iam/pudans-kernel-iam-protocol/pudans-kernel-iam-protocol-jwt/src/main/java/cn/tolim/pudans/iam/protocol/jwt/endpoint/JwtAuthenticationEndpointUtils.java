
package cn.tolim.pudans.iam.protocol.jwt.endpoint;

import cn.tolim.pudans.iam.protocol.jwt.exception.JwtAuthenticationException;
import cn.tolim.pudans.iam.protocol.jwt.exception.JwtError;


public class JwtAuthenticationEndpointUtils {

    public static void throwError(JwtError jwtError) {
        throw new JwtAuthenticationException(jwtError);
    }
}
