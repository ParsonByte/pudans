package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Service action declared by an application
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServiceActionDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Action name in the form {appCode}:{action}
    private String name;

    // Related resource type code
    private String resourceType;

    // Action description
    private String description;
}
