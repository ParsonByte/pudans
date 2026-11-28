package cn.tolim.pudans.iam.infra.security.authentication;

import cn.tolim.pudans.iam.infra.geo.GeoLocation;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgent;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

public class WebAuthenticationDetails
        extends
        org.springframework.security.web.authentication.WebAuthenticationDetails {

    private static final long serialVersionUID = 1L;

    private final UserAgent userAgent;
    private final GeoLocation geoLocation;
    private final LocalDateTime authenticationTime;
    private AuthenticationProvider authenticationProvider;

    public WebAuthenticationDetails(HttpServletRequest request, UserAgent userAgent,
                                    GeoLocation geoLocation) {
        super(request);
        this.userAgent = userAgent;
        this.geoLocation = geoLocation;
        this.authenticationTime = LocalDateTime.now();
    }

    public WebAuthenticationDetails(String remoteAddress, String sessionId, UserAgent userAgent,
                                    GeoLocation geoLocation,
                                    AuthenticationProvider authenticationProvider,
                                    LocalDateTime authenticationTime) {
        super(remoteAddress, sessionId);
        this.userAgent = userAgent;
        this.geoLocation = geoLocation;
        this.authenticationProvider = authenticationProvider;
        this.authenticationTime = authenticationTime;
    }

    @JsonProperty("authenticationTime")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    public LocalDateTime getAuthenticationTime() {
        return authenticationTime;
    }

    @JsonProperty("authenticationProvider")
    public AuthenticationProvider getAuthenticationProvider() {
        return authenticationProvider;
    }

    public void setAuthenticationProvider(AuthenticationProvider authenticationProvider) {
        this.authenticationProvider = authenticationProvider;
    }

    @JsonProperty("userAgent")
    public UserAgent getUserAgent() {
        return userAgent;
    }

    @JsonProperty("geoLocation")
    public GeoLocation getGeoLocation() {
        return geoLocation;
    }
}
