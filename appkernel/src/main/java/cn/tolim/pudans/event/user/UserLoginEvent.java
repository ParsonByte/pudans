package cn.tolim.pudans.event.user;

import cn.tolim.pudans.contract.user.User;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class UserLoginEvent extends ApplicationEvent {

    @Getter
    private final User user;

    // 携带登录用户，供登录后处理使用。
    public UserLoginEvent(Object source, User user) {
        super(source);
        this.user = user;
    }
}
