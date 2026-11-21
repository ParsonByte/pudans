package cn.tolim.pudans.security.authentication.twofactor.totp;

public interface TotpSecrets {

    // 在允许的时间偏差内匹配验证码，未命中返回 null。
    Long matchStep(String rawSecret, int code, long nowMillis);

    // 生成新的 Base32 密钥，用于用户绑定认证器。
    String generateTotpSecret();

    // 加密明文密钥，避免敏感数据以明文持久化。
    String encryptSecret(String rawSecret);

    // 解密已存储的密钥，仅在服务端校验时调用。
    String decryptSecret(String encryptedSecret);
}
