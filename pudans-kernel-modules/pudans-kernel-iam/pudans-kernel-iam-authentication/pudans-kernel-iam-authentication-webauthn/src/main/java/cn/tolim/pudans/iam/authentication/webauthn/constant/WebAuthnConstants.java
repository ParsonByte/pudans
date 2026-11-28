package cn.tolim.pudans.iam.authentication.webauthn.constant;

import cn.tolim.pudans.iam.infra.constant.IamConstants;

// Paths and protocol constants of WebAuthn authentication
public final class WebAuthnConstants {

    // WebAuthn MFA management API base path
    public static final String API_PATH = IamConstants.API_PREFIX + "/auth/mfa/webauthn";

    // Client data type for registration ceremonies
    public static final String TYPE_CREATE = "webauthn.create";

    // Client data type for assertion ceremonies
    public static final String TYPE_GET = "webauthn.get";

    // Attestation format: no attestation statement
    public static final String ATTESTATION_NONE = "none";

    // Attestation format: packed (self or certificate attestation)
    public static final String ATTESTATION_PACKED = "packed";

    // Attestation format: FIDO U2F
    public static final String ATTESTATION_FIDO_U2F = "fido-u2f";

    // Public key credential type
    public static final String CREDENTIAL_TYPE_PUBLIC_KEY = "public-key";

    // Authenticator data flag: user present
    public static final int FLAG_USER_PRESENT = 0x01;

    // Authenticator data flag: user verified
    public static final int FLAG_USER_VERIFIED = 0x04;

    // Authenticator data flag: backup eligible
    public static final int FLAG_BACKUP_ELIGIBLE = 0x08;

    // Authenticator data flag: backup state
    public static final int FLAG_BACKUP_STATE = 0x10;

    // Authenticator data flag: attested credential data included
    public static final int FLAG_ATTESTED_CREDENTIAL_DATA = 0x40;

    // Authenticator data flag: extension data included
    public static final int FLAG_EXTENSION_DATA = (byte) 0x80;

    // Minimum authenticator data length (rpIdHash, flags, signCount)
    public static final int MIN_AUTHENTICATOR_DATA_LENGTH = 37;

    private WebAuthnConstants() {
    }
}