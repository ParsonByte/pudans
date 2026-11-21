package cn.tolim.pudans.core.storage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.core.infra.crypto.HmacTokenSigner;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.PudansProperties;

@Component
public class TransferTokens {

    private static final String KEY_FILE = "storage-token.key";

    private static final String DOWNLOAD = "download";

    private static final String UPLOAD = "upload";

    private static final String OAUTH = "oauth";

    private static final String SEPARATOR = ".";

    private static final String FIELD_SEPARATOR = "|";

    private final HmacTokenSigner signer;

    public TransferTokens(PudansProperties properties) {
        this.signer = new HmacTokenSigner(properties.getWorkDir().resolve("keys").resolve(KEY_FILE));
    }

    public String signDownload(String fileId, Duration ttl) {
        return sign(DOWNLOAD, fileId, ttl);
    }

    public String signUpload(String fileId, Duration ttl) {
        return sign(UPLOAD, fileId, ttl);
    }

    public String signOAuthState(String policyName, Duration ttl) {
        return sign(OAUTH, policyName, ttl);
    }

    public void verifyDownload(String token, String fileId) {
        verify(DOWNLOAD, token, fileId);
    }

    public void verifyUpload(String token, String fileId) {
        verify(UPLOAD, token, fileId);
    }

    public String verifyOAuthState(String token) {
        var payload = parse(token);
        if (!OAUTH.equals(payload.type())) {
            throw invalid();
        }
        return payload.id();
    }

    private String sign(String type, String id, Duration ttl) {
        var expiresAt = Instant.now().plus(ttl).getEpochSecond();
        var payload = type + FIELD_SEPARATOR + id + FIELD_SEPARATOR + expiresAt;
        var encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + SEPARATOR + signer.sign(encoded);
    }

    private void verify(String type, String token, String id) {
        var payload = parse(token);
        if (!type.equals(payload.type()) || !id.equals(payload.id())) {
            throw invalid();
        }
    }

    private TokenPayload parse(String token) {
        if (token == null || token.isBlank()) {
            throw invalid();
        }
        var separator = token.indexOf(SEPARATOR);
        if (separator <= 0 || separator == token.length() - 1) {
            throw invalid();
        }
        var encoded = token.substring(0, separator);
        var signature = token.substring(separator + 1);
        if (!signer.verify(encoded, signature)) {
            throw invalid();
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        var fields = payload.split("\\|");
        if (fields.length != 3) {
            throw invalid();
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(fields[2]);
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw invalid();
        }
        return new TokenPayload(fields[0], fields[1], expiresAt);
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "storage-token-invalid", "存储令牌无效或已过期。");
    }

    private record TokenPayload(String type, String id, long expiresAt) {}
}
