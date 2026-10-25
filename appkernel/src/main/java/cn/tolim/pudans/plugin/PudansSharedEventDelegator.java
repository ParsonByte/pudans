package cn.tolim.pudans.plugin;

import java.util.Objects;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


@Getter
class PudansSharedEventDelegator extends ApplicationEvent {

    private final ApplicationEvent delegate;

    public PudansSharedEventDelegator(Object source, ApplicationEvent delegate) {
        super(source);
        this.delegate = delegate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PudansSharedEventDelegator that = (PudansSharedEventDelegator) o;
        return Objects.equals(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(delegate);
    }
}
