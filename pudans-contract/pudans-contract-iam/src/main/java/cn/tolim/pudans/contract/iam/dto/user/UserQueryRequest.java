package cn.tolim.pudans.contract.iam.dto.user;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// User query request
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserQueryRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Keyword matched against username, name, email and phone
    private String keyword;

    // Organization ID filter
    private String orgId;

    // User status filter
    private String status;

    // Page number starting at zero
    private Integer page;

    // Page size
    private Integer size;
}
