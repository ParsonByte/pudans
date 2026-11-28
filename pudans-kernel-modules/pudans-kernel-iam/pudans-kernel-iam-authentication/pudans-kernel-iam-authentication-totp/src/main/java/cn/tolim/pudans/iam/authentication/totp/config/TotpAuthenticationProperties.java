package cn.tolim.pudans.iam.authentication.totp.config;

import cn.tolim.pudans.iam.authentication.totp.enums.TotpAlgorithm;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// Configuration of TOTP authentication and its secret encryption key
@ConfigurationProperties(prefix = "pudans.iam.totp")
public class TotpAuthenticationProperties {

    // Enables TOTP authentication
    private boolean enabled = true;

    // Issuer shown in authenticator applications
    private String issuer = "PUDANS";

    // Default TOTP hash algorithm
    private TotpAlgorithm algorithm = TotpAlgorithm.SHA1;

    // Default number of code digits
    private int digits = 6;

    // Default time step size in seconds
    private int periodSeconds = 30;

    // Accepted time-step window on each side
    private int window = 1;

    // Number of random bytes in a generated secret
    private int secretBytes = 20;

    // Consecutive failures before a credential is locked
    private int maxFailedAttempts = 5;

    // Lock duration after too many failures
    private Duration lockDuration = Duration.ofMinutes(5);

    // Secret encryption settings
    private final Crypto crypto = new Crypto();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public TotpAlgorithm getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(TotpAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    public int getDigits() {
        return digits;
    }

    public void setDigits(int digits) {
        this.digits = digits;
    }

    public int getPeriodSeconds() {
        return periodSeconds;
    }

    public void setPeriodSeconds(int periodSeconds) {
        this.periodSeconds = periodSeconds;
    }

    public int getWindow() {
        return window;
    }

    public void setWindow(int window) {
        this.window = window;
    }

    public int getSecretBytes() {
        return secretBytes;
    }

    public void setSecretBytes(int secretBytes) {
        this.secretBytes = secretBytes;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }

    public Crypto getCrypto() {
        return crypto;
    }

    // Spissatus facade settings for TOTP secret encryption
    public static class Crypto {

        // Logical key ID protecting TOTP secrets
        private String keyId = "pudans-totp-secret";

        // Spissatus key provider: memory, file, environment or keystore
        private String keyProvider = "memory";

        // Directory of the file key provider
        private String fileDirectory;

        // Master password of the file key provider
        private String masterPassword;

        // Environment variable prefix of the environment key provider
        private String environmentPrefix;

        // Path of the keystore key provider
        private String keystorePath;

        // Password of the keystore key provider
        private String keystorePassword;

        // Optional spissatus compliance mode
        private String complianceMode;

        // Tenant used for spissatus keys
        private String tenantId = "default";

        // Caller identity presented to spissatus
        private String callerId = "pudans-iam-totp";

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKeyProvider() {
            return keyProvider;
        }

        public void setKeyProvider(String keyProvider) {
            this.keyProvider = keyProvider;
        }

        public String getFileDirectory() {
            return fileDirectory;
        }

        public void setFileDirectory(String fileDirectory) {
            this.fileDirectory = fileDirectory;
        }

        public String getMasterPassword() {
            return masterPassword;
        }

        public void setMasterPassword(String masterPassword) {
            this.masterPassword = masterPassword;
        }

        public String getEnvironmentPrefix() {
            return environmentPrefix;
        }

        public void setEnvironmentPrefix(String environmentPrefix) {
            this.environmentPrefix = environmentPrefix;
        }

        public String getKeystorePath() {
            return keystorePath;
        }

        public void setKeystorePath(String keystorePath) {
            this.keystorePath = keystorePath;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public void setKeystorePassword(String keystorePassword) {
            this.keystorePassword = keystorePassword;
        }

        public String getComplianceMode() {
            return complianceMode;
        }

        public void setComplianceMode(String complianceMode) {
            this.complianceMode = complianceMode;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getCallerId() {
            return callerId;
        }

        public void setCallerId(String callerId) {
            this.callerId = callerId;
        }
    }
}