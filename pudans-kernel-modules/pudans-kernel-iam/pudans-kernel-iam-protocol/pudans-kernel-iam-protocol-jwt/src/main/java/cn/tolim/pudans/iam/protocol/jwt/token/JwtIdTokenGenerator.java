
package cn.tolim.pudans.iam.protocol.jwt.token;

import cn.tolim.pudans.iam.infra.util.CertUtils;
import cn.tolim.pudans.iam.protocol.jwt.exception.IdTokenGenerateException;
import io.jsonwebtoken.Jwts;

import java.security.PrivateKey;
import java.time.Instant;
import java.util.Date;


public class JwtIdTokenGenerator implements IdTokenGenerator {

    @Override
    public IdToken generate(IdTokenContext context) {
        try{
            Instant issuedAt = Instant.now();
            Instant expiresAt=issuedAt.plus(context.getIdTokenTimeToLive());
            
            PrivateKey rsaPrivateKey = CertUtils.readPrivateKey(context.getPrivateKey(), "");
            
            String tokenValue = Jwts.builder().issuer(context.getIssuer())
                    .issuedAt(new Date(issuedAt.toEpochMilli()))
                    .subject(context.getSubject())
                    .audience().add(context.getAudience()).and()
                    .expiration(new Date(expiresAt.toEpochMilli()))
                    .signWith(rsaPrivateKey, Jwts.SIG.RS256)
                    .compact();
            return IdToken.builder().tokenValue(tokenValue)
                    .issuedAt(issuedAt)
                    .expiresAt(expiresAt)
                    .build();
        }catch (Exception e){
            throw new IdTokenGenerateException(e);
        }
    }
}
