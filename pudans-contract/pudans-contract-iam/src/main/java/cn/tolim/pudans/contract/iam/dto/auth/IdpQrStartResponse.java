package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Identity provider QR login start result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdpQrStartResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // One-time login state
    private String state;

    // Universal scan URL or provider authorization URL
    private String url;

    // State expiration time
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;
}