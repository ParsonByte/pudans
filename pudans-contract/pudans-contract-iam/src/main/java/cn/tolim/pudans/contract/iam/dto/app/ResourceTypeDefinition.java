package cn.tolim.pudans.contract.iam.dto.app;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Resource type declared by an application
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResourceTypeDefinition implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Resource type code in the form {appCode}:{resourceType}
    private String code;

    // Resource type name
    private String name;

    // Resource pattern
    private String pattern;

    // Resource type description
    private String description;
}
