package cn.tolim.pudans.iam.authentication.webauthn.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

// Relying party configuration of WebAuthn authentication
@ConfigurationProperties(prefix = "pudans.iam.webauthn")
public class WebAuthnProperties {

    // Enables WebAuthn authentication
    private boolean enabled = true;

    // Relying party ID, must equal the registrable domain of the origins
    private String relyingPartyId = "localhost";

    // Relying party display name
    private String relyingPartyName = "PUDANS";

    // Allowed browser origins, when empty the relying party ID is used as host
    private List<String> origins = new ArrayList<>();

    // Challenge lifetime
    private Duration challengeTtl = Duration.ofMinutes(5);

    // Random challenge length in bytes
    private int challengeBytes = 32;

    // Ceremony timeout advertised to the browser
    private Duration timeout = Duration.ofSeconds(60);

    // Maximum credentials per user
    private int maxCredentialsPerUser = 10;

    // Requires user verification during registration and assertion
    private boolean requireUserVerification = false;

    // Attestation conveyance preference
    private String attestation = "none";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRelyingPartyId() {
        return relyingPartyId;
    }

    public void setRelyingPartyId(String relyingPartyId) {
        this.relyingPartyId = relyingPartyId;
    }

    public String getRelyingPartyName() {
        return relyingPartyName;
    }

    public void setRelyingPartyName(String relyingPartyName) {
        this.relyingPartyName = relyingPartyName;
    }

    public List<String> getOrigins() {
        return origins;
    }

    public void setOrigins(List<String> origins) {
        this.origins = origins;
    }

    public Duration getChallengeTtl() {
        return challengeTtl;
    }

    public void setChallengeTtl(Duration challengeTtl) {
        this.challengeTtl = challengeTtl;
    }

    public int getChallengeBytes() {
        return challengeBytes;
    }

    public void setChallengeBytes(int challengeBytes) {
        this.challengeBytes = challengeBytes;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxCredentialsPerUser() {
        return maxCredentialsPerUser;
    }

    public void setMaxCredentialsPerUser(int maxCredentialsPerUser) {
        this.maxCredentialsPerUser = maxCredentialsPerUser;
    }

    public boolean isRequireUserVerification() {
        return requireUserVerification;
    }

    public void setRequireUserVerification(boolean requireUserVerification) {
        this.requireUserVerification = requireUserVerification;
    }

    public String getAttestation() {
        return attestation;
    }

    public void setAttestation(String attestation) {
        this.attestation = attestation;
    }
}