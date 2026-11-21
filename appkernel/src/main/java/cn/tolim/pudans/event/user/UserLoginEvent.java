package cn.tolim.pudans.event.user;

import cn.tolim.pudans.contract.user.resource.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class UserLoginEvent extends ApplicationEvent {

    @Getter
    private final User user;

    public UserLoginEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
