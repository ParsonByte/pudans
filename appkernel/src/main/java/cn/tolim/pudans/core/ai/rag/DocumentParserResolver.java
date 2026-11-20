package cn.tolim.pudans.core.ai.rag;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

@Component
public class DocumentParserResolver {

    private final List<DocumentParser> parsers;

    public DocumentParserResolver(List<DocumentParser> parsers) {
        var ordered = new ArrayList<>(parsers);
        AnnotationAwareOrderComparator.sort(ordered);
        this.parsers = List.copyOf(ordered);
    }

    public DocumentParser resolve(String fileName, @Nullable String mimeType) {
        return parsers.stream()
                .filter(parser -> parser.supports(fileName, mimeType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文档类型。"));
    }
}