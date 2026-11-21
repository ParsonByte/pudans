package cn.tolim.pudans.core.infra.r2dbc.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.r2dbc.spi.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiKbChunkRepositoryTest {

    @Mock
    Row row;

    @Test
    void mapsACandidateRow() {
        when(row.get("id", Long.class)).thenReturn(11L);
        when(row.get("document_id", Long.class)).thenReturn(22L);
        when(row.get("kb_name", String.class)).thenReturn("kb-a");
        when(row.get("file_name", String.class)).thenReturn("manual.pdf");
        when(row.get("content", String.class)).thenReturn("切片内容");
        when(row.get("page_no", Integer.class)).thenReturn(3);
        when(row.get("heading_path", String.class)).thenReturn("第一章 > 1.1");
        when(row.get("score", Double.class)).thenReturn(0.75);

        var candidate = AiKbChunkRepository.candidate(row);

        assertThat(candidate.chunkId()).isEqualTo(11L);
        assertThat(candidate.documentId()).isEqualTo(22L);
        assertThat(candidate.kbName()).isEqualTo("kb-a");
        assertThat(candidate.fileName()).isEqualTo("manual.pdf");
        assertThat(candidate.content()).isEqualTo("切片内容");
        assertThat(candidate.pageNo()).isEqualTo(3);
        assertThat(candidate.headingPath()).isEqualTo("第一章 > 1.1");
        assertThat(candidate.score()).isEqualTo(0.75);
    }

    @Test
    void defaultsMissingOptionalColumns() {
        when(row.get("id", Long.class)).thenReturn(11L);
        when(row.get("document_id", Long.class)).thenReturn(22L);
        when(row.get("kb_name", String.class)).thenReturn("kb-a");
        when(row.get("file_name", String.class)).thenReturn("manual.pdf");
        when(row.get("content", String.class)).thenReturn("切片内容");

        var candidate = AiKbChunkRepository.candidate(row);

        assertThat(candidate.pageNo()).isNull();
        assertThat(candidate.headingPath()).isNull();
        assertThat(candidate.score()).isZero();
    }

    @Test
    void rejectsARowWithoutARequiredIdentifier() {
        assertThatThrownBy(() -> AiKbChunkRepository.candidate(row))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("id");
    }
}
