package cn.tolim.pudans.contract.audit.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Audit geographic point
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditGeoPoint implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // Latitude
    private Double lat;

    // Longitude
    private Double lon;
}
