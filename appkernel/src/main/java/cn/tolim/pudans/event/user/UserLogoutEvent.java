package cn.tolim.pudans.event.user;

import cn.tolim.pudans.contract.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class UserLogoutEvent extends ApplicationEvent {

    @Getter
    private final User user;

    // 携带登出用户，供登出后处理使用。
    public UserLogoutEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
