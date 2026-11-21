package cn.tolim.pudans.security.authentication.twofactor.totp;

public interface TotpSecrets {

    Long matchStep(String rawSecret, int code, long nowMillis);

    String generateTotpSecret();

    String encryptSecret(String rawSecret);

    String decryptSecret(String encryptedSecret);
}
