package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Identity provider QR login start request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdpQrStartRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Optional provider type for a direct authorization page: dingtalk_oauth or feishu_oauth
    private String type;
}