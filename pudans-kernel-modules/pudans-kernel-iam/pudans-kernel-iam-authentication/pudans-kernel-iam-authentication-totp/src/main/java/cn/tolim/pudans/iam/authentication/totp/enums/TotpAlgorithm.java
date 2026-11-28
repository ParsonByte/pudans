package cn.tolim.pudans.iam.authentication.totp.enums;

// Hash algorithm used by RFC 6238 TOTP
public enum TotpAlgorithm {
    // HMAC-SHA1, the RFC 6238 default and the most widely compatible option
    SHA1("HmacSHA1"),

    // HMAC-SHA256
    SHA256("HmacSHA256"),

    // HMAC-SHA512
    SHA512("HmacSHA512");

    private final String jcaName;

    TotpAlgorithm(String jcaName) {
        this.jcaName = jcaName;
    }

    // JCA MAC algorithm name
    public String getJcaName() {
        return jcaName;
    }
}