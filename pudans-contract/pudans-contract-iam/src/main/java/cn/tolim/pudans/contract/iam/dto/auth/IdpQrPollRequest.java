package cn.tolim.pudans.contract.iam.dto.auth;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Identity provider QR login poll request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class IdpQrPollRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // One-time login state
    private String state;
}