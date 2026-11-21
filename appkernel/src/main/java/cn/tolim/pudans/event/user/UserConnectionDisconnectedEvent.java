package cn.tolim.pudans.event.user;

import cn.tolim.pudans.contract.user.resource.UserConnection;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class UserConnectionDisconnectedEvent extends ApplicationEvent {

    @Getter
    private final UserConnection userConnection;

    public UserConnectionDisconnectedEvent(Object source, UserConnection userConnection) {
        super(source);
        this.userConnection = userConnection;
    }
}
