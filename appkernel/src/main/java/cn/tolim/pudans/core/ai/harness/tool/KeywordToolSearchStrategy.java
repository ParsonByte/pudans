package cn.tolim.pudans.core.ai.harness.tool;

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class KeywordToolSearchStrategy implements ToolSearchStrategy {

    private static final int NAME_MATCH_WEIGHT = 3;

    private static final int DESCRIPTION_MATCH_WEIGHT = 1;

    @Override
    public ToolSearchResult search(ToolSearchRequest request) {
        var terms = Arrays.stream(request.query().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(term -> !term.isBlank())
                .distinct()
                .toList();
        var matches = request.candidates().stream()
                .map(tool -> new Match(tool, score(tool, terms)))
                .filter(match -> match.score() > 0)
                .sorted(Comparator.comparingInt(Match::score)
                        .reversed()
                        .thenComparing(match -> match.tool().name()))
                .limit(request.limit())
                .map(match -> match.tool().name())
                .toList();
        return new ToolSearchResult(matches);
    }

    private static int score(ToolSpecification tool, List<String> terms) {
        var name = tool.name().toLowerCase(Locale.ROOT);
        var description = tool.description() == null
                ? ""
                : tool.description().toLowerCase(Locale.ROOT);
        var score = 0;
        for (var term : terms) {
            if (name.contains(term)) {
                score += NAME_MATCH_WEIGHT;
            }
            if (description.contains(term)) {
                score += DESCRIPTION_MATCH_WEIGHT;
            }
        }
        return score;
    }

    private record Match(ToolSpecification tool, int score) {}
}