package cn.tolim.pudans.contract.audit.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Audit target
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditTarget implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Target ID
    private String id;

    // Target name
    private String name;

    // Target type code
    private String type;
}
