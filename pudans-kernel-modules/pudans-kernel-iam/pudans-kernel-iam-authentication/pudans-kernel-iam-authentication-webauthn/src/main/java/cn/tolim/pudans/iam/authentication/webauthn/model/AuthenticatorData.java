package cn.tolim.pudans.iam.authentication.webauthn.model;

import cn.tolim.pudans.iam.authentication.webauthn.constant.WebAuthnConstants;
import cn.tolim.pudans.iam.authentication.webauthn.cose.CborDecoder;
import cn.tolim.pudans.iam.authentication.webauthn.exception.WebAuthnVerificationException;

import java.util.Arrays;

// Parsed WebAuthn authenticator data
public final class AuthenticatorData {

    private final byte[] raw;
    private final byte[] rpIdHash;
    private final int flags;
    private final long signCount;
    private final byte[] aaguid;
    private final byte[] credentialId;
    private final byte[] credentialPublicKey;

    private AuthenticatorData(byte[] raw, byte[] rpIdHash, int flags, long signCount,
                              byte[] aaguid, byte[] credentialId, byte[] credentialPublicKey) {
        this.raw = raw;
        this.rpIdHash = rpIdHash;
        this.flags = flags;
        this.signCount = signCount;
        this.aaguid = aaguid;
        this.credentialId = credentialId;
        this.credentialPublicKey = credentialPublicKey;
    }

    // Parses authenticator data produced by a browser ceremony
    public static AuthenticatorData parse(byte[] data) {
        if (data == null || data.length < WebAuthnConstants.MIN_AUTHENTICATOR_DATA_LENGTH) {
            throw new WebAuthnVerificationException("Authenticator data is too short");
        }
        byte[] rpIdHash = Arrays.copyOfRange(data, 0, 32);
        int flags = data[32] & 0xFF;
        long signCount = ((data[33] & 0xFFL) << 24)
                | ((data[34] & 0xFFL) << 16)
                | ((data[35] & 0xFFL) << 8)
                | (data[36] & 0xFFL);
        byte[] aaguid = null;
        byte[] credentialId = null;
        byte[] credentialPublicKey = null;
        if ((flags & WebAuthnConstants.FLAG_ATTESTED_CREDENTIAL_DATA) != 0) {
            int offset = WebAuthnConstants.MIN_AUTHENTICATOR_DATA_LENGTH;
            if (data.length < offset + 18) {
                throw new WebAuthnVerificationException(
                        "Attested credential data is truncated");
            }
            aaguid = Arrays.copyOfRange(data, offset, offset + 16);
            offset += 16;
            int credentialIdLength = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
            offset += 2;
            if (credentialIdLength < 1 || data.length < offset + credentialIdLength) {
                throw new WebAuthnVerificationException("Credential ID is truncated");
            }
            credentialId = Arrays.copyOfRange(data, offset, offset + credentialIdLength);
            offset += credentialIdLength;
            if (data.length <= offset) {
                throw new WebAuthnVerificationException("Credential public key is missing");
            }
            byte[] remaining = Arrays.copyOfRange(data, offset, data.length);
            CborDecoder.Decoded decoded = CborDecoder.decodeFirst(remaining);
            credentialPublicKey = Arrays.copyOfRange(remaining, 0, decoded.consumed());
        }
        return new AuthenticatorData(data.clone(), rpIdHash, flags, signCount, aaguid,
                credentialId, credentialPublicKey);
    }

    public byte[] raw() {
        return raw.clone();
    }

    public byte[] rpIdHash() {
        return rpIdHash.clone();
    }

    public int flags() {
        return flags;
    }

    public long signCount() {
        return signCount;
    }

    public byte[] aaguid() {
        return aaguid == null ? null : aaguid.clone();
    }

    public byte[] credentialId() {
        return credentialId == null ? null : credentialId.clone();
    }

    public byte[] credentialPublicKey() {
        return credentialPublicKey == null ? null : credentialPublicKey.clone();
    }

    public boolean userPresent() {
        return (flags & WebAuthnConstants.FLAG_USER_PRESENT) != 0;
    }

    public boolean userVerified() {
        return (flags & WebAuthnConstants.FLAG_USER_VERIFIED) != 0;
    }

    public boolean attestedCredentialDataIncluded() {
        return (flags & WebAuthnConstants.FLAG_ATTESTED_CREDENTIAL_DATA) != 0;
    }

    public boolean backupEligible() {
        return (flags & WebAuthnConstants.FLAG_BACKUP_ELIGIBLE) != 0;
    }

    public boolean backupState() {
        return (flags & WebAuthnConstants.FLAG_BACKUP_STATE) != 0;
    }
}