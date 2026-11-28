package cn.tolim.pudans.audit.event;

import cn.tolim.pudans.audit.entity.Actor;
import cn.tolim.pudans.audit.entity.Event;
import cn.tolim.pudans.audit.entity.GeoLocation;
import cn.tolim.pudans.audit.entity.Target;
import cn.tolim.pudans.audit.entity.UserAgent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;
import java.util.List;

@Getter
public class AuditEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 3425943796938543659L;

    private final String requestId;

    private final String sessionId;

    private final Actor actor;

    private final Event event;

    private final List<Target> targets;

    private final UserAgent userAgent;

    private final GeoLocation geoLocation;

    public AuditEvent(String requestId, String sessionId, Actor actor, Event event,
                      UserAgent userAgent, GeoLocation geoLocation, List<Target> targets) {
        super(event);
        this.requestId = requestId;
        this.sessionId = sessionId;
        this.actor = actor;
        this.event = event;
        this.targets = targets;
        this.userAgent = userAgent;
        this.geoLocation = geoLocation;
    }
}