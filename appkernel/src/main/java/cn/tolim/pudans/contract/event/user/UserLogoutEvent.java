package cn.tolim.pudans.contract.event.user;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.plugin.SharedEvent;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


@SharedEvent
public class UserLogoutEvent extends ApplicationEvent {

    @Getter
    private final User user;

    public UserLogoutEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
