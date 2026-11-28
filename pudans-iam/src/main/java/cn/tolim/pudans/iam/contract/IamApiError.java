package cn.tolim.pudans.iam.contract;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

public class IamApiError implements Serializable {

    @Serial
    private static final long serialVersionUID = 5314090731282226503L;

    @JsonProperty("error")
    private final String error;

    @JsonProperty("error_description")
    private final String description;

    @JsonProperty("status")
    private final int status;

    @JsonProperty("timestamp")
    private final LocalDateTime timestamp;

    public IamApiError(String error, String description, int status) {
        this.error = error;
        this.description = description;
        this.status = status;
        this.timestamp = LocalDateTime.now();
    }

    public String getError() {
        return error;
    }

    public String getDescription() {
        return description;
    }

    public int getStatus() {
        return status;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }
}