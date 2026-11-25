package cn.tolim.pudans.contract.ai.dto;

public record AiProviderCapability(String code, String label, String status) {

    public static final String AVAILABLE = "available";

    public static final String UNVERIFIED = "unverified";

    public static final String UNSUPPORTED = "unsupported";

    public AiProviderCapability {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("provider capability code must not be blank");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("provider capability label must not be blank");
        }
        if (!AVAILABLE.equals(status) && !UNVERIFIED.equals(status) && !UNSUPPORTED.equals(status)) {
            throw new IllegalArgumentException("unsupported provider capability status: " + status);
        }
        code = code.trim();
        label = label.trim();
    }
}
