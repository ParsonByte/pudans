package cn.tolim.pudans.contract.audit.dto;

import java.io.Serial;
import java.io.Serializable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Audit geographic location
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditGeoLocation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    // IP address
    private String ip;

    // Continent code
    private String continentCode;

    // Continent name
    private String continentName;

    // Country code
    private String countryCode;

    // Country name
    private String countryName;

    // Province code
    private String provinceCode;

    // Province name
    private String provinceName;

    // City code
    private String cityCode;

    // City name
    private String cityName;

    // Geographic point
    private AuditGeoPoint point;

    // Geo location provider
    private String provider;
}
