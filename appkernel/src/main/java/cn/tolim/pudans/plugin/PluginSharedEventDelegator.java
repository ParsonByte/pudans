package cn.tolim.pudans.plugin;

import java.util.Objects;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;


@Getter
class PluginSharedEventDelegator extends ApplicationEvent {


    private final ApplicationEvent delegate;

    public PluginSharedEventDelegator(Object source, ApplicationEvent delegate) {
        super(source);
        this.delegate = delegate;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PluginSharedEventDelegator that = (PluginSharedEventDelegator) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(delegate);
    }
}
