package cn.tolim.pudans.infra.utils;

import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
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

    // 配置资源并启用 Kubernetes 风格文档匹配。
    public YamlUnstructuredLoader(Resource... resources) {
        setResources(resources);
        setDocumentMatchers(DEFAULT_UNSTRUCTURED_MATCHER);
    }

    // 逐个解析匹配的 YAML 文档为扩展对象。
    public List<Unstructured> load() {
        List<Unstructured> unstructuredList = new ArrayList<>();
        process((properties, map) -> {
            Unstructured unstructured = JsonUtils.mapToObject(map, Unstructured.class);
            unstructuredList.add(unstructured);
        });
        return unstructuredList;
    }
}
