package cn.tolim.pudans.notification.sender;

// Normalized result returned by every provider sender.
public record SendResult(boolean success, String providerMessageId, String response, String error) {

    public static SendResult success(String providerMessageId, String response) {
        return new SendResult(true, providerMessageId, response, null);
    }

    public static SendResult failure(String error) {
        return new SendResult(false, null, null, error);
    }

    public static SendResult failure(String error, String response) {
        return new SendResult(false, null, response, error);
    }
}