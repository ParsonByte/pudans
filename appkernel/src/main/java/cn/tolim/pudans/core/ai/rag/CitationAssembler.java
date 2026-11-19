package cn.tolim.pudans.core.ai.rag;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CitationAssembler {

    private static final String HEADER = "【知识库资料】";

    public KnowledgePrompt assemble(List<RetrievedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return new KnowledgePrompt("", List.of());
        }
        var citations = new ArrayList<Citation>();
        var prompt = new StringBuilder(HEADER);
        for (var chunk : chunks) {
            var candidate = chunk.candidate();
            var citation = new Citation(
                    citations.size() + 1,
                    candidate.documentId(),
                    candidate.fileName(),
                    candidate.pageNo(),
                    candidate.headingPath(),
                    chunk.score(),
                    candidate.content());
            citations.add(citation);
            prompt.append('\n').append(renderReference(citation)).append('\n').append(citation.snippet());
        }
        return new KnowledgePrompt(prompt.toString(), List.copyOf(citations));
    }

    private static String renderReference(Citation citation) {
        var reference = new StringBuilder()
                .append('[').append(citation.index()).append("]《").append(citation.fileName()).append('》');
        if (citation.pageNo() != null) {
            reference.append("第 ").append(citation.pageNo()).append(" 页");
        }
        if (citation.headingPath() != null && !citation.headingPath().isBlank()) {
            reference.append(citation.pageNo() != null ? " · " : "· ").append(citation.headingPath());
        }
        return reference.toString();
    }

    public record KnowledgePrompt(String prompt, List<Citation> citations) {

        public KnowledgePrompt {
            prompt = prompt == null ? "" : prompt;
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }
}
