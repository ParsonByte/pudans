package cn.tolim.pudans.event.user;

import cn.tolim.pudans.contract.user.UserConnection;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class UserConnectionDisconnectedEvent extends ApplicationEvent {

    @Getter
    private final UserConnection userConnection;

    // 携带已断开的用户连接，供状态清理使用。
    public UserConnectionDisconnectedEvent(Object source, UserConnection userConnection) {
        super(source);
        this.userConnection = userConnection;
    }
}
