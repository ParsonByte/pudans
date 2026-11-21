package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class HybridRetrievalTest {

    private final HybridRetrieval service = new HybridRetrieval();

    @Test
    void fusesRankedListsWithReciprocalRankFusion() {
        var vector = List.of(candidate(1, 0.9, "aa"), candidate(2, 0.8, "bb"));
        var keyword = List.of(candidate(3, 0.7, "cc"), candidate(1, 0.6, "aa"));

        var results = service.retrieve(vector, keyword, new RetrievalOptions(60, 0.0, 10, 1000));

        assertThat(results).extracting(result -> result.candidate().chunkId()).containsExactly(1L, 3L, 2L);
        assertThat(results.get(0).score()).isCloseTo(0.991935, within(1e-6));
    }

    @Test
    void honorsTheConfiguredRrfConstant() {
        var vector = List.of(candidate(1, 0.9, "aa"), candidate(2, 0.8, "bb"));

        var results = service.retrieve(vector, List.of(), new RetrievalOptions(1, 0.0, 10, 1000));

        assertThat(results.get(0).score()).isEqualTo(1.0);
        assertThat(results.get(1).score()).isCloseTo(2.0 / 3, within(1e-9));
    }

    @Test
    void deduplicatesRepeatedCandidatesWithinAList() {
        var vector = List.of(candidate(1, 0.5, "aa"), candidate(1, 0.9, "aa"), candidate(2, 0.4, "bb"));

        var results = service.retrieve(vector, List.of(), new RetrievalOptions(60, 0.0, 10, 1000));

        assertThat(results).extracting(result -> result.candidate().chunkId()).containsExactly(1L, 2L);
        assertThat(results.get(0).score()).isEqualTo(1.0);
    }

    @Test
    void filtersCandidatesBelowTheScoreThreshold() {
        var vector = List.of(candidate(1, 0.9, "aa"), candidate(2, 0.8, "bb"), candidate(3, 0.7, "cc"));

        var results = service.retrieve(vector, List.of(), new RetrievalOptions(60, 0.99, 10, 1000));

        assertThat(results).extracting(result -> result.candidate().chunkId()).containsExactly(1L);
    }

    @Test
    void limitsResultsByTopK() {
        var vector = List.of(
                candidate(1, 0.9, "aa"),
                candidate(2, 0.8, "bb"),
                candidate(3, 0.7, "cc"),
                candidate(4, 0.6, "dd"),
                candidate(5, 0.5, "ee"));

        var results = service.retrieve(vector, List.of(), new RetrievalOptions(60, 0.0, 2, 1000));

        assertThat(results).extracting(result -> result.candidate().chunkId()).containsExactly(1L, 2L);
    }

    @Test
    void truncatesTheLastCandidateToFitTheCharacterBudget() {
        var vector = List.of(candidate(1, 0.9, "abcdefgh"), candidate(2, 0.8, "ijkl"));

        var results = service.retrieve(vector, List.of(), new RetrievalOptions(60, 0.0, 10, 10));

        assertThat(results).extracting(result -> result.candidate().content()).containsExactly("abcdefgh", "ij");
    }

    @Test
    void truncatesASingleCandidateLargerThanTheBudget() {
        var vector = List.of(candidate(1, 0.9, "abcdefgh"));

        var results = service.retrieve(vector, List.of(), new RetrievalOptions(60, 0.0, 10, 5));

        assertThat(results).singleElement().extracting(result -> result.candidate().content()).isEqualTo("abcde");
    }

    @Test
    void returnsEmptyResultsWhenNoCandidatesAreProvided() {
        var results = service.retrieve(null, List.of(), new RetrievalOptions(60, 0.0, 10, 1000));

        assertThat(results).isEmpty();
    }

    @Test
    void rejectsInvalidRetrievalOptions() {
        assertThatThrownBy(() -> new RetrievalOptions(0, 0.3, 5, 4000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetrievalOptions(60, 1.5, 5, 4000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.retrieve(List.of(), List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RetrievalCandidate candidate(long chunkId, double score, String content) {
        return new RetrievalCandidate(chunkId, chunkId + 100, "kb-a", "manual.pdf", content, 1, "第一章", score);
    }
}
