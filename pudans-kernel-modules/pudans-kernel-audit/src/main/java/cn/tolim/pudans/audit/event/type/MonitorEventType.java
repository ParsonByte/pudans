package cn.tolim.pudans.audit.event.type;

import java.util.List;

import cn.tolim.pudans.audit.event.Type;
import cn.tolim.pudans.iam.infra.security.userdetails.UserType;
import static cn.tolim.pudans.audit.event.ConsoleResource.SESSION_RESOURCE;

public class MonitorEventType {

    public static final Type DOWN_LINE_SESSION = new Type("pudans:event:monitor:down_line_session", "下线会话",
        SESSION_RESOURCE, List.of(UserType.ADMIN));

}
