package cn.tolim.pudans.iam.infra.security.authentication;

import cn.tolim.pudans.iam.infra.geo.GeoLocation;
import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import cn.tolim.pudans.iam.infra.util.IpUtils;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgent;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.util.Assert;

public class WebAuthenticationDetailsSource
        implements
        AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> {

    private final GeoLocationParser geoLocationParser;
    private final UserAgentParser userAgentParser;

    public WebAuthenticationDetailsSource() {
        this.geoLocationParser = null;
        this.userAgentParser = null;
    }

    public WebAuthenticationDetailsSource(GeoLocationParser geoLocationParser,
                                          UserAgentParser userAgentParser) {
        Assert.notNull(geoLocationParser, "geoLocationParser 不能为空");
        Assert.notNull(userAgentParser, "userAgentParser 不能为空");
        this.geoLocationParser = geoLocationParser;
        this.userAgentParser = userAgentParser;
    }

    @Override
    public WebAuthenticationDetails buildDetails(HttpServletRequest request) {
        GeoLocation geoLocation = geoLocationParser == null ? new GeoLocation()
                : geoLocationParser.getGeoLocation(IpUtils.getIpAddr(request));
        UserAgent userAgent = userAgentParser == null ? UserAgent.builder().build()
                : userAgentParser.getUserAgent(request);
        return new WebAuthenticationDetails(request, userAgent, geoLocation);
    }
}
