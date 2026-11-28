package cn.tolim.pudans.communal.core.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public class JwtTokenService {

    private static final JWSAlgorithm ALGORITHM = JWSAlgorithm.HS256;
    private static final String CLAIM_USERNAME = "preferred_username";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_ORGANIZATION_ID = "org_id";
    private static final int MINIMUM_SECRET_BYTES = 32;

    private final byte[] secret;
    private final String issuer;
    private final String audience;
    private final long ttlSeconds;

    public JwtTokenService(String secret, String issuer, String audience, long ttlSeconds) {
        if (secret == null) {
            throw new IllegalArgumentException("密钥不能为空");
        }
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("密钥长度至少为 " + MINIMUM_SECRET_BYTES + " 字节");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("issuer 不能为空");
        }
        if (audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("audience 不能为空");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds 必须为正数");
        }
        this.secret = secretBytes;
        this.issuer = issuer;
        this.audience = audience;
        this.ttlSeconds = ttlSeconds;
    }

    private static void putClaim(JWTClaimsSet.Builder claims, String name, String value) {
        if (value != null) {
            claims.claim(name, value);
        }
    }

    public String issue(AuthenticatedUser user) {
        if (user == null) {
            throw new IllegalArgumentException("用户不能为空");
        }
        if (user.userId() == null || user.userId().isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        Instant issuedAt = Instant.now();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(user.userId())
                .issuer(issuer)
                .audience(audience)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(issuedAt.plusSeconds(ttlSeconds)));
        putClaim(claims, CLAIM_USERNAME, user.username());
        putClaim(claims, CLAIM_NAME, user.displayName());
        putClaim(claims, CLAIM_ORGANIZATION_ID, user.organizationId());
        JWSHeader header = new JWSHeader.Builder(ALGORITHM)
                .type(JOSEObjectType.JWT)
                .build();
        SignedJWT jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException exception) {
            throw new IllegalStateException("JWT 签名失败", exception);
        }
        return jwt.serialize();
    }

    public AuthenticatedUser parse(String token) {
        if (token == null || token.isBlank()) {
            throw new JwtValidationException("JWT 不能为空");
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!ALGORITHM.equals(jwt.getHeader().getAlgorithm())) {
                throw new JwtValidationException("JWT 算法必须为 HS256");
            }
            if (!JOSEObjectType.JWT.equals(jwt.getHeader().getType())) {
                throw new JwtValidationException("JWT 类型必须为 JWT");
            }
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new JwtValidationException("JWT 签名校验失败");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!issuer.equals(claims.getIssuer())) {
                throw new JwtValidationException("JWT 签发方不可信");
            }
            List<String> audiences = claims.getAudience();
            if (audiences == null || !audiences.contains(audience)) {
                throw new JwtValidationException("JWT 受众不可信");
            }
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !expiration.toInstant().isAfter(Instant.now())) {
                throw new JwtValidationException("JWT 已过期");
            }
            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                throw new JwtValidationException("JWT 缺少主体");
            }
            return new AuthenticatedUser(
                    userId,
                    claims.getStringClaim(CLAIM_USERNAME),
                    claims.getStringClaim(CLAIM_NAME),
                    claims.getStringClaim(CLAIM_ORGANIZATION_ID));
        } catch (ParseException | JOSEException exception) {
            throw new JwtValidationException("JWT 校验失败", exception);
        }
    }
}