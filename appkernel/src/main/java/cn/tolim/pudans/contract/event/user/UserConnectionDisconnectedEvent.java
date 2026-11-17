package cn.tolim.pudans.contract.event.user;

import cn.tolim.pudans.contract.core.extension.user.UserConnection;
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
