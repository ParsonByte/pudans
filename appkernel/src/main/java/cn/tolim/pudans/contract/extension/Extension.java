package cn.tolim.pudans.contract.extension;

import java.util.Comparator;
import java.util.Objects;


public interface Extension extends ExtensionOperator, Comparable<Extension> {

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
