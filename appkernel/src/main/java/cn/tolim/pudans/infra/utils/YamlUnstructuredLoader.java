package cn.tolim.pudans.infra.utils;

import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.core.io.Resource;


public class YamlUnstructuredLoader extends YamlProcessor {

    private static final DocumentMatcher DEFAULT_UNSTRUCTURED_MATCHER = properties -> {
        if (properties.containsKey("apiVersion")
                && properties.containsKey("kind")
                && (properties.containsKey("metadata.name") || properties.containsKey("metadata.generateName"))) {
            return YamlProcessor.MatchStatus.FOUND;
        }
        return MatchStatus.NOT_FOUND;
    };

    public YamlUnstructuredLoader(Resource... resources) {
        setResources(resources);
        setDocumentMatchers(DEFAULT_UNSTRUCTURED_MATCHER);
    }

    public List<Unstructured> load() {
        List<Unstructured> unstructuredList = new ArrayList<>();
        process((properties, map) -> {
            Unstructured unstructured = JsonUtils.mapToObject(map, Unstructured.class);
            unstructuredList.add(unstructured);
        });
        return unstructuredList;
    }
}
