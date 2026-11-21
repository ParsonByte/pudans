package cn.tolim.pudans.extension.contract;

import java.time.Instant;
import java.util.Comparator;

public enum Comparators {
    ;

    // 按创建时间比较，参数控制升序或降序。
    public static <E extends Extension> Comparator<E> compareCreationTimestamp(boolean asc) {
        var comparator = Comparator.<E, Instant>comparing(e -> e.getMetadata().getCreationTimestamp());
        return asc ? comparator : comparator.reversed();
    }

    // 按元数据名称比较，参数控制升序或降序。
    public static <E extends Extension> Comparator<E> compareName(boolean asc) {
        var comparator = Comparator.<E, String>comparing(e -> e.getMetadata().getName());
        return asc ? comparator : comparator.reversed();
    }

    // 默认新对象优先，同一时间再按名称升序。
    public static <T extends Extension> Comparator<T> defaultComparator() {
        Comparator<T> comparator = compareCreationTimestamp(false);
        comparator = comparator.thenComparing(compareName(true));
        return comparator;
    }


    // 升序时空值排末尾，降序时空值排最前。
    public static Comparator<Object> nullsComparator(boolean isAscending) {
        return isAscending
                ? org.springframework.util.comparator.Comparators.nullsHigh()
                : org.springframework.util.comparator.Comparators.nullsLow();
    }
}
