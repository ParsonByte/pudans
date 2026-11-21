package cn.tolim.pudans.core.storage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.infra.crypto.HmacTokenSigner;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.PudansProperties;

@Component
public class TransferTokens {

    private static final String KEY_FILE = "storage-token.key";

    private static final String DOWNLOAD = "download";

    private static final String UPLOAD = "upload";

    private static final String OAUTH = "oauth";

    private static final String SEPARATOR = ".";

    private static final String FIELD_SEPARATOR = "|";

    private final HmacTokenSigner signer;

    // 使用工作目录下的独立密钥签发存储令牌。
    public TransferTokens(PudansProperties properties) {
        this.signer = new HmacTokenSigner(properties.getWorkDir().resolve("keys").resolve(KEY_FILE));
    }

    // 签发只允许下载指定文件的限时令牌。
    public String signDownload(String fileId, Duration ttl) {
        return sign(DOWNLOAD, fileId, ttl);
    }

    // 签发只允许上传指定文件的限时令牌。
    public String signUpload(String fileId, Duration ttl) {
        return sign(UPLOAD, fileId, ttl);
    }

    // 签发绑定策略名称的 OAuth 状态令牌。
    public String signOAuthState(String policyName, Duration ttl) {
        return sign(OAUTH, policyName, ttl);
    }

    // 校验下载令牌的用途、对象与有效期。
    public void verifyDownload(String token, String fileId) {
        verify(DOWNLOAD, token, fileId);
    }

    // 校验上传令牌的用途、对象与有效期。
    public void verifyUpload(String token, String fileId) {
        verify(UPLOAD, token, fileId);
    }

    // 校验 OAuth 状态令牌并返回策略名称。
    public String verifyOAuthState(String token) {
        var payload = parse(token);
        if (!OAUTH.equals(payload.type())) {
            throw invalid();
        }
        return payload.id();
    }

    // 拼接负载并附加 HMAC 签名。
    private String sign(String type, String id, Duration ttl) {
        var expiresAt = Instant.now().plus(ttl).getEpochSecond();
        var payload = type + FIELD_SEPARATOR + id + FIELD_SEPARATOR + expiresAt;
        var encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + SEPARATOR + signer.sign(encoded);
    }

    // 校验令牌用途与绑定对象是否一致。
    private void verify(String type, String token, String id) {
        var payload = parse(token);
        if (!type.equals(payload.type()) || !id.equals(payload.id())) {
            throw invalid();
        }
    }

    // 验签并解析负载，异常统一按无效令牌处理。
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

    // 构造统一的无效令牌错误。
    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "storage-token-invalid", "存储令牌无效或已过期。");
    }

    private record TokenPayload(String type, String id, long expiresAt) {}
}
