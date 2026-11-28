package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Application accessible by the current user
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyApplicationSummary implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Application code
    private String appCode;

    // Application name
    private String name;

    // Icon URL
    private String icon;

    // Application base URL
    private String baseUrl;

    // Application description
    private String description;

    // Application group ID
    private String groupId;

    // Application group name
    private String groupName;

    // Display order
    private Integer sort;

    // Integration protocol
    private String protocol;
}
