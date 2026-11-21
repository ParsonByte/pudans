package cn.tolim.pudans.extension.contract;

import static org.springframework.data.domain.Sort.Order.asc;
import static org.springframework.data.domain.Sort.Order.desc;

import cn.tolim.pudans.extension.contract.index.query.Queries;
import cn.tolim.pudans.extension.contract.index.query.Query;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.data.domain.Sort;

public enum ExtensionUtil {
    ;


    public static final String DO_NOT_OVERWRITE_LABEL = "pudans.tolim.cn/do-not-overwrite";


    // 检查保留标签，防止扩展被同步流程覆盖。
    public static boolean hasDoNotOverwriteLabel(ExtensionOperator extension) {
        if (extension.getMetadata() == null) {
            return false;
        }
        var labels = extension.getMetadata().getLabels();
        return labels != null && Boolean.parseBoolean(labels.get(DO_NOT_OVERWRITE_LABEL));
    }

    // 删除时间戳存在即视为已进入删除流程。
    public static boolean isDeleted(ExtensionOperator extension) {
        return extension.getMetadata() != null && extension.getMetadata().getDeletionTimestamp() != null;
    }

    // 仅在确有新增时写回，避免无谓更新。
    public static boolean addFinalizers(MetadataOperator metadata, Set<String> finalizers) {
        var modifiableFinalizers =
                new HashSet<>(metadata.getFinalizers() == null ? Collections.emptySet() : metadata.getFinalizers());
        var added = modifiableFinalizers.addAll(finalizers);
        if (added) {
            metadata.setFinalizers(modifiableFinalizers);
        }
        return added;
    }

    // 仅在确有移除时写回，避免无谓更新。
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


    // 构造排除正在删除对象的查询条件。
    public static Query notDeleting() {
        return Queries.isNull("metadata.deletionTimestamp");
    }


    // 统一默认排序：新建优先，其次按名称升序。
    public static Sort defaultSort() {
        return Sort.by(desc("metadata.creationTimestamp"), asc("metadata.name"));
    }
}
