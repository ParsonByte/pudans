package cn.tolim.pudans.contract.iam.dto.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Batch user lookup result
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserBatchResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Users found
    private List<UserSummary> users;
}
