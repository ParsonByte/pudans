
package cn.tolim.pudans.iam.protocol.jwt.token;

import cn.tolim.pudans.iam.infra.util.CertUtils;
import io.jsonwebtoken.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.PublicKey;


@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);


    public static Claims parserToken(String token, String publicKey) {
        try {
            PublicKey readPublicKey = CertUtils.readPublicKey(publicKey, "");
            JwtParser jwtParser = Jwts.parser().verifyWith(readPublicKey).build();

            return jwtParser.parseSignedClaims(token).getPayload();
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            logger.info("JWT 签名无效。");
            logger.trace("JWT 签名无效，堆栈：{}", e.getMessage());
            throw e;
        } catch (ExpiredJwtException e) {
            logger.info("JWT 令牌已过期。");
            logger.trace("JWT 令牌已过期，堆栈：{}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            logger.info("不支持的 JWT 令牌。");
            logger.trace("不支持的 JWT 令牌，堆栈：{}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            logger.info("JWT 令牌 compact 处理无效。");
            logger.trace("JWT 令牌 compact 处理无效，堆栈：{}", e.getMessage());
            throw e;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
