package cn.tolim.pudans.audit.entity;

import cn.tolim.pudans.iam.infra.geo.GeoLocationProvider;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeoLocation implements Serializable {

    @Serial
    private static final long serialVersionUID = -1144169992714000310L;

    private String ip;

    private String continentCode;

    private String continentName;

    private String countryCode;

    private String countryName;

    private String provinceCode;

    private String provinceName;

    private String cityCode;

    private String cityName;

    private GeoPoint point;

    private GeoLocationProvider provider;
}