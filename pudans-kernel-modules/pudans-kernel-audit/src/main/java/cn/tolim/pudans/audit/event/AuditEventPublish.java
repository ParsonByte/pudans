package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.Actor;
import cn.tolim.pudans.audit.entity.Event;
import cn.tolim.pudans.audit.entity.GeoLocation;
import cn.tolim.pudans.audit.entity.GeoPoint;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.entity.UserAgent;
import cn.tolim.pudans.audit.enums.EventStatus;
import cn.tolim.pudans.audit.event.type.EventType;
import cn.tolim.pudans.iam.infra.geo.GeoLocationParser;
import cn.tolim.pudans.iam.infra.security.authentication.AuthenticationProvider;
import cn.tolim.pudans.iam.infra.security.authentication.WebAuthenticationDetails;
import cn.tolim.pudans.iam.infra.security.userdetails.UserDetails;
import cn.tolim.pudans.iam.infra.trace.TraceUtils;
import cn.tolim.pudans.iam.infra.util.IpUtils;
import cn.tolim.pudans.iam.infra.web.useragent.UserAgentParser;
import com.alibaba.fastjson2.JSONObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class AuditEventPublish {

    private static final Logger logger = LoggerFactory.getLogger(AuditEventPublish.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    private final ObjectProvider<GeoLocationParser> geoLocationParserProvider;

    private final ObjectProvider<UserAgentParser> userAgentParserProvider;

    public AuditEventPublish(ApplicationEventPublisher applicationEventPublisher,
                             ObjectProvider<GeoLocationParser> geoLocationParserProvider,
                             ObjectProvider<UserAgentParser> userAgentParserProvider) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.geoLocationParserProvider = geoLocationParserProvider;
        this.userAgentParserProvider = userAgentParserProvider;
    }

    public void publish(EventType eventType, Principal principal, EventStatus eventStatus,
                        List<Target> targets) {
        Assert.notNull(eventType, "eventType 不能为空");
        Assert.notNull(eventStatus, "eventStatus 不能为空");
        Authentication authentication = principal instanceof Authentication value ? value : null;
        Event event = buildEvent(eventType, eventStatus);
        if (principal != null) {
            event.setContent(principal.getName() + "：" + eventType.getDesc());
        }
        Actor actor = authentication == null ? buildActor(principal) : buildActor(authentication);
        publishEvent(event, actor, targets, authentication);
    }

    public void publish(EventType eventType, Authentication authentication, EventStatus eventStatus,
                        List<Target> targets) {
        Assert.notNull(eventType, "eventType 不能为空");
        Assert.notNull(eventStatus, "eventStatus 不能为空");
        Event event = buildEvent(eventType, eventStatus);
        if (authentication != null) {
            Map<String, String> content = new LinkedHashMap<>();
            String authType = getAuthenticationType(authentication);
            if (authType != null) {
                content.put("auth_type", authType);
            }
            content.put("desc", authentication.getName() + "：" + eventType.getDesc());
            event.setContent(JSONObject.toJSONString(content));
        }
        publishEvent(event, buildActor(authentication), targets, authentication);
    }

    public void publish(EventType eventType, String content, EventStatus eventStatus) {
        Assert.notNull(eventType, "eventType 不能为空");
        Assert.notNull(eventStatus, "eventStatus 不能为空");
        Event event = buildEvent(eventType, eventStatus);
        event.setContent(content);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        publishEvent(event, buildActor(authentication), null, authentication);
    }

    public void publish(EventType eventType, String content, Actor actor, EventStatus eventStatus) {
        Assert.notNull(eventType, "eventType 不能为空");
        Assert.notNull(eventStatus, "eventStatus 不能为空");
        Event event = buildEvent(eventType, eventStatus);
        event.setContent(content);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        publishEvent(event, actor == null ? buildActor(authentication) : actor, null, authentication);
    }

    public void publish(EventType eventType, Map<String, Object> parameters, String content,
                        List<Target> targets, String result, EventStatus eventStatus, Actor actor) {
        Assert.notNull(eventType, "eventType 不能为空");
        Assert.notNull(eventStatus, "eventStatus 不能为空");
        Event event = buildEvent(eventType, eventStatus);
        if (parameters != null) {
            try {
                event.setParam(JSONObject.toJSONString(parameters));
            } catch (RuntimeException e) {
                event.setParam(parameters.toString());
            }
        }
        event.setContent(content);
        event.setResult(result);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        publishEvent(event, actor == null ? buildActor(authentication) : actor, targets,
                authentication);
    }

    public void publish(EventType eventType, List<Target> targets, String result,
                        EventStatus eventStatus) {
        Assert.notNull(eventType, "eventType 不能为空");
        Assert.notNull(eventStatus, "eventStatus 不能为空");
        Event event = buildEvent(eventType, eventStatus);
        event.setResult(result);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        publishEvent(event, buildActor(authentication), targets, authentication);
    }

    public static Actor getActor() {
        return buildActor(SecurityContextHolder.getContext().getAuthentication());
    }

    private static Event buildEvent(EventType eventType, EventStatus eventStatus) {
        return Event.builder().type(eventType).time(LocalDateTime.now()).status(eventStatus).build();
    }

    private static Actor buildActor(Principal principal) {
        if (principal == null) {
            return null;
        }
        return Actor.builder().id(principal.getName()).build();
    }

    private static Actor buildActor(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Authentication nested) {
            return buildActor(nested);
        }
        Actor.ActorBuilder builder = Actor.builder();
        if (principal instanceof UserDetails userDetails) {
            builder.id(userDetails.getId()).type(userDetails.getUserType());
        } else if (principal instanceof String name) {
            builder.id(name);
        } else if (principal != null) {
            builder.id(principal.toString());
        }
        String authType = getAuthenticationType(authentication);
        if (authType != null) {
            builder.authType(authType);
        }
        return builder.build();
    }

    private static String getAuthenticationType(Authentication authentication) {
        if (authentication.getDetails() instanceof WebAuthenticationDetails details) {
            AuthenticationProvider provider = details.getAuthenticationProvider();
            return provider == null ? null : provider.getType();
        }
        return null;
    }

    private void publishEvent(Event event, Actor actor, List<Target> targets,
                              Authentication authentication) {
        HttpServletRequest request = currentRequest();
        String requestId = TraceUtils.get();
        String sessionId = null;
        if (request != null) {
            if (requestId == null) {
                requestId = TraceUtils.getRequestId(request);
            }
            HttpSession session = request.getSession(false);
            sessionId = session == null ? null : session.getId();
        }
        applicationEventPublisher.publishEvent(new AuditEvent(requestId, sessionId, actor, event,
                resolveUserAgent(authentication, request), resolveGeoLocation(authentication, request),
                targets));
    }

    private static HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletRequestAttributes) {
            return servletRequestAttributes.getRequest();
        }
        return null;
    }

    private UserAgent resolveUserAgent(Authentication authentication, HttpServletRequest request) {
        if (authentication != null
                && authentication.getDetails() instanceof WebAuthenticationDetails details) {
            UserAgent userAgent = toUserAgent(details.getUserAgent());
            if (userAgent != null) {
                return userAgent;
            }
        }
        if (request == null) {
            return null;
        }
        UserAgentParser parser = userAgentParserProvider.getIfAvailable();
        if (parser == null) {
            return null;
        }
        try {
            return toUserAgent(parser.getUserAgent(request));
        } catch (RuntimeException e) {
            logger.debug("解析用户代理失败：{}", e.getMessage());
            return null;
        }
    }

    private static UserAgent toUserAgent(cn.tolim.pudans.iam.infra.web.useragent.UserAgent value) {
        if (value == null) {
            return null;
        }
        return UserAgent.builder().browser(value.getBrowser()).browserType(value.getBrowserType())
                .browserMajorVersion(value.getBrowserMajorVersion()).platform(value.getPlatform())
                .platformVersion(value.getPlatformVersion()).deviceType(value.getDeviceType())
                .build();
    }

    private GeoLocation resolveGeoLocation(Authentication authentication,
                                           HttpServletRequest request) {
        if (authentication != null
                && authentication.getDetails() instanceof WebAuthenticationDetails details) {
            GeoLocation geoLocation = toGeoLocation(details.getGeoLocation());
            if (geoLocation != null) {
                return geoLocation;
            }
        }
        if (request == null) {
            return null;
        }
        GeoLocationParser parser = geoLocationParserProvider.getIfAvailable();
        if (parser == null) {
            return null;
        }
        try {
            return toGeoLocation(parser.getGeoLocation(IpUtils.getIpAddr(request)));
        } catch (RuntimeException e) {
            logger.debug("解析地理位置失败：{}", e.getMessage());
            return null;
        }
    }

    private static GeoLocation toGeoLocation(
            cn.tolim.pudans.iam.infra.geo.GeoLocation value) {
        if (value == null) {
            return null;
        }
        GeoPoint point = null;
        if (Objects.nonNull(value.getLatitude()) && Objects.nonNull(value.getLongitude())) {
            point = new GeoPoint(value.getLatitude(), value.getLongitude());
        }
        return GeoLocation.builder().ip(value.getIp()).continentCode(value.getContinentCode())
                .continentName(value.getContinentName()).countryCode(value.getCountryCode())
                .countryName(value.getCountryName()).provinceCode(value.getProvinceCode())
                .provinceName(value.getProvinceName()).cityCode(value.getCityCode())
                .cityName(value.getCityName()).point(point).provider(value.getProvider()).build();
    }
}