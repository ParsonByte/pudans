package cn.tolim.pudans.gateway.security;

public record SessionValidationResult(boolean active, String subject, String reason) {

    public static SessionValidationResult active(String subject) {
        return new SessionValidationResult(true, subject, null);
    }

    public static SessionValidationResult inactive(String reason) {
        return new SessionValidationResult(false, null, reason);
    }
}