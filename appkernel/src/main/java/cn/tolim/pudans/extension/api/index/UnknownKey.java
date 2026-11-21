package cn.tolim.pudans.extension.api.index;

import java.util.Objects;
import org.jspecify.annotations.Nullable;


public record UnknownKey(@Nullable String value) implements Comparable<UnknownKey> {

    @Override
    public int compareTo(UnknownKey o) {
        return KeyComparator.INSTANCE.compare(this.value, o.value);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        UnknownKey unknownKey = (UnknownKey) o;
        return Objects.equals(value, unknownKey.value);
    }

    @Override
    public String toString() {
        return Objects.toString(value);
    }
}
