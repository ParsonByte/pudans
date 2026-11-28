package cn.tolim.pudans.communal.core.security;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtTokenServiceTests {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String ISSUER = "pudans-iam";
    private static final String AUDIENCE = "pudans-apps";

    private final JwtTokenService tokenService = new JwtTokenService(SECRET, ISSUER, AUDIENCE, 3600);

    private static SignedJWT sign(JWTClaimsSet claims, JWSHeader header) throws Exception {
        SignedJWT jwt = new SignedJWT(header, claims);
        jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt;
    }

    private static JWSHeader header(JOSEObjectType type) {
        return new JWSHeader.Builder(JWSAlgorithm.HS256).type(type).build();
    }

    @Test
    void shouldIssueAndParseRoundTrip() {
        AuthenticatedUser user = new AuthenticatedUser("user-1", "alice", "Alice", "org-1");

        AuthenticatedUser parsed = tokenService.parse(tokenService.issue(user));

        assertEquals(user, parsed);
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenService(null, ISSUER, AUDIENCE, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenService("short", ISSUER, AUDIENCE, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenService(SECRET, " ", AUDIENCE, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenService(SECRET, ISSUER, null, 60));
        assertThrows(IllegalArgumentException.class,
                () -> new JwtTokenService(SECRET, ISSUER, AUDIENCE, 0));
    }

    @Test
    void shouldRejectMissingUserWhenIssuing() {
        assertThrows(IllegalArgumentException.class, () -> tokenService.issue(null));
        assertThrows(IllegalArgumentException.class,
                () -> tokenService.issue(new AuthenticatedUser(" ", "alice", "Alice", null)));
    }

    @Test
    void shouldRejectBlankToken() {
        assertThrows(JwtValidationException.class, () -> tokenService.parse(null));
        assertThrows(JwtValidationException.class, () -> tokenService.parse(" "));
    }

    @Test
    void shouldRejectTamperedToken() {
        String token = tokenService.issue(new AuthenticatedUser("user-1", "alice", "Alice", null));

        assertThrows(JwtValidationException.class, () -> tokenService.parse(token + "x"));
        assertThrows(JwtValidationException.class, () -> tokenService.parse(token.substring(0, 10)));
    }

    @Test
    void shouldRejectWrongIssuerOrAudience() {
        String token = tokenService.issue(new AuthenticatedUser("user-1", "alice", "Alice", null));
        JwtTokenService wrongIssuer = new JwtTokenService(SECRET, "other-issuer", AUDIENCE, 3600);
        JwtTokenService wrongAudience = new JwtTokenService(SECRET, ISSUER, "other-audience", 3600);

        assertThrows(JwtValidationException.class, () -> wrongIssuer.parse(token));
        assertThrows(JwtValidationException.class, () -> wrongAudience.parse(token));
    }

    @Test
    void shouldRejectExpiredToken() throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now.minusSeconds(120)))
                .expirationTime(Date.from(now.minusSeconds(60)))
                .build();
        String token = sign(claims, header(JOSEObjectType.JWT)).serialize();

        assertThrows(JwtValidationException.class, () -> tokenService.parse(token));
    }

    @Test
    void shouldRejectTokenWithoutSubject() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(new Date())
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build();
        String token = sign(claims, header(JOSEObjectType.JWT)).serialize();

        assertThrows(JwtValidationException.class, () -> tokenService.parse(token));
    }

    @Test
    void shouldRejectWrongHeaderType() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build();
        String token = sign(claims, header(JOSEObjectType.JOSE)).serialize();

        assertThrows(JwtValidationException.class, () -> tokenService.parse(token));
    }

    @Test
    void shouldRejectTokenSignedWithAnotherSecret() throws Exception {
        byte[] otherSecret = "fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .expirationTime(Date.from(Instant.now().plusSeconds(60)))
                .build();
        SignedJWT jwt = new SignedJWT(header(JOSEObjectType.JWT), claims);
        jwt.sign(new MACSigner(otherSecret));

        assertThrows(JwtValidationException.class, () -> tokenService.parse(jwt.serialize()));
    }
}