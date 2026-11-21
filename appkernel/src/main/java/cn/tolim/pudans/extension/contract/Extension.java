package cn.tolim.pudans.extension.contract;

import java.util.Comparator;
import java.util.Objects;


public interface Extension extends ExtensionOperator, Comparable<Extension> {

    // 按名称比较，元数据缺失的实例排在末尾。
    @Override
    default int compareTo(Extension another) {
        if (another == null || another.getMetadata() == null) {
            return 1;
        }
        if (getMetadata() == null) {
            return -1;
        }
        return Objects.compare(getMetadata().getName(), another.getMetadata().getName(), Comparator.naturalOrder());
    }
}
