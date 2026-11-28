package cn.tolim.pudans.contract.repository.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// File download URL result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DownloadUrlResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Download URL
    private String url;

    // URL expiration time when the URL is temporary
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiresAt;

    // Storage provider code
    private String provider;

    // Whether the URL can be handed directly to clients
    private Boolean direct;
}
