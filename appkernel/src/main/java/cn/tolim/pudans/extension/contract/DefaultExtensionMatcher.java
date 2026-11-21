package cn.tolim.pudans.extension.contract;

import cn.tolim.pudans.extension.contract.index.query.Condition;
import cn.tolim.pudans.extension.contract.index.query.Queries;
import cn.tolim.pudans.extension.contract.router.selector.FieldSelector;
import cn.tolim.pudans.extension.contract.router.selector.LabelSelector;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

@Getter
@RequiredArgsConstructor
@Builder(builderMethodName = "internalBuilder")
public class DefaultExtensionMatcher implements ExtensionMatcher {
    private final ExtensionClient client;
    private final GroupVersionKind gvk;
    private final LabelSelector labelSelector;
    private final FieldSelector fieldSelector;

    public static DefaultExtensionMatcherBuilder builder(ExtensionClient client, GroupVersionKind gvk) {
        return internalBuilder().client(client).gvk(gvk);
    }


    @Override
    public boolean match(Extension extension) {
        if (!gvk.equals(extension.groupVersionKind())) {
            return false;
        }
        if (!hasFieldSelector() && !hasLabelSelector()) {
            return true;
        }
        var listOptions = new ListOptions();
        listOptions.setLabelSelector(labelSelector);
        var fieldQuery = Queries.equal("metadata.name", extension.getMetadata().getName());
        if (hasFieldSelector()) {
            fieldQuery = fieldQuery.and((Condition) fieldSelector.query());
        }
        listOptions.setFieldSelector(new FieldSelector(fieldQuery));
        return client.countBy(extension.getClass(), listOptions) > 0;
    }

    boolean hasFieldSelector() {
        return fieldSelector != null && fieldSelector.query() != null;
    }

    boolean hasLabelSelector() {
        return labelSelector != null && !CollectionUtils.isEmpty(labelSelector.getConditions());
    }
}
