package cn.tolim.pudans.extension.api;

import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;

import cn.tolim.pudans.extension.api.index.query.Queries;
import cn.tolim.pudans.extension.api.index.query.Query;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Sort;

public enum ExtensionUtil {
    ;


    public static final String DO_NOT_OVERWRITE_LABEL = "pudans.tolim.cn/do-not-overwrite";


    public static boolean hasDoNotOverwriteLabel(ExtensionOperator extension) {
        if (extension.getMetadata() == null) {
            return false;
        }
        var labels = extension.getMetadata().getLabels();
        return labels != null && Boolean.parseBoolean(labels.get(DO_NOT_OVERWRITE_LABEL));
    }

    public static boolean isDeleted(ExtensionOperator extension) {
        return extension.getMetadata() != null && extension.getMetadata().getDeletionTimestamp() != null;
    }

    public static boolean addFinalizers(MetadataOperator metadata, Set<String> finalizers) {
        var modifiableFinalizers =
                new HashSet<>(metadata.getFinalizers() == null ? Collections.emptySet() : metadata.getFinalizers());
        var added = modifiableFinalizers.addAll(finalizers);
        if (added) {
            metadata.setFinalizers(modifiableFinalizers);
        }
        return added;
    }

    public static boolean removeFinalizers(MetadataOperator metadata, Set<String> finalizers) {
        if (metadata.getFinalizers() == null) {
            return false;
        }
        var existingFinalizers = new HashSet<>(metadata.getFinalizers());
        var removed = existingFinalizers.removeAll(finalizers);
        if (removed) {
            metadata.setFinalizers(existingFinalizers);
        }
        return removed;
    }


    public static Query notDeleting() {
        return Queries.isNull("metadata.deletionTimestamp");
    }


    public static Sort defaultSort() {
        return Sort.by(desc("metadata.creationTimestamp"), asc("metadata.name"));
    }
}
