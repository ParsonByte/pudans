package cn.tolim.pudans.event.user;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class PasswordChangedEvent extends ApplicationEvent {
    private final String username;

    // 通知密码已变更，供清理旧会话使用。
    public PasswordChangedEvent(Object source, String username) {
        super(source);
        this.username = username;
    }
}
