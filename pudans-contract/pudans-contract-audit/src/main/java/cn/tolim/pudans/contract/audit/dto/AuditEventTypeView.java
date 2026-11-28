package cn.tolim.pudans.contract.audit.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

// Audit event type metadata
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditEventTypeView implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Event type code
    private String code;

    // Event type name
    private String name;

    // Audit category code
    private String category;

    // Audit category name
    private String categoryName;

    // Related resource code
    private String resourceCode;

    // Related resource name
    private String resourceName;

    // User types allowed to produce the event
    private List<String> userTypes;
}
