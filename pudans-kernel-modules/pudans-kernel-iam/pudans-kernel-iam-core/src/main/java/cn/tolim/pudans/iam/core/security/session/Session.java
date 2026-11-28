package cn.tolim.pudans.iam.core.security.session;

import cn.tolim.pudans.iam.infra.constant.IamConstants;
import cn.tolim.pudans.iam.infra.geo.GeoLocation;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgent;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;


@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public class Session implements Serializable {

    @Serial
    private static final long serialVersionUID = 8850489178248613501L;
    // User ID
    private final String id;
    // Username
    private final String username;
    // Session ID
    private String sessionId;
    // Location-related
    private GeoLocation geoLocation;

    // userAgent
    private UserAgent userAgent;

    // User type
    private UserType userType;

    // Authentication type
    private String authenticationProvider;

    // Login time
    @JsonFormat(pattern = IamConstants.DEFAULT_DATE_TIME_FORMATTER_PATTERN)
    private LocalDateTime authenticationTime;

    // Last request time
    @JsonFormat(pattern = IamConstants.DEFAULT_DATE_TIME_FORMATTER_PATTERN)
    private LocalDateTime lastRequestTime;

}
