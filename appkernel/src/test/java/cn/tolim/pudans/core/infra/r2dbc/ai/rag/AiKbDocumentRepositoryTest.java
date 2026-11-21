package cn.tolim.pudans.core.infra.r2dbc.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.rag.DocumentStatus;
import io.r2dbc.spi.Row;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiKbDocumentRepositoryTest {

    @Mock
    Row row;

    @Test
    void mapsADocumentRow() {
        when(row.get("id", Long.class)).thenReturn(42L);
        when(row.get("kb_name", String.class)).thenReturn("kb-a");
        when(row.get("storage_file_id", String.class)).thenReturn("file-1");
        when(row.get("file_name", String.class)).thenReturn("manual.pdf");
        when(row.get("mime_type", String.class)).thenReturn("application/pdf");
        when(row.get("file_size", Long.class)).thenReturn(2048L);
        when(row.get("checksum", String.class)).thenReturn("sum");
        when(row.get("status", String.class)).thenReturn("READY");
        when(row.get("chunk_count", Integer.class)).thenReturn(3);
        when(row.get("token_count", Integer.class)).thenReturn(99);
        when(row.get("error_msg", String.class)).thenReturn("none");
        when(row.get("version", Integer.class)).thenReturn(2);
        when(row.get("uploaded_by", String.class)).thenReturn("alice");
        when(row.get("created_at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.parse("2026-01-02T03:04:05Z"));
        when(row.get("updated_at", OffsetDateTime.class))
                .thenReturn(OffsetDateTime.parse("2026-01-03T04:05:06Z"));

        var document = AiKbDocumentRepository.document(row);

        assertThat(document.getId()).isEqualTo(42L);
        assertThat(document.getKbName()).isEqualTo("kb-a");
        assertThat(document.getStorageFileId()).isEqualTo("file-1");
        assertThat(document.getFileName()).isEqualTo("manual.pdf");
        assertThat(document.getMimeType()).isEqualTo("application/pdf");
        assertThat(document.getFileSize()).isEqualTo(2048L);
        assertThat(document.getChecksum()).isEqualTo("sum");
        assertThat(document.getStatus()).isEqualTo(DocumentStatus.READY);
        assertThat(document.getChunkCount()).isEqualTo(3);
        assertThat(document.getTokenCount()).isEqualTo(99);
        assertThat(document.getErrorMsg()).isEqualTo("none");
        assertThat(document.getVersion()).isEqualTo(2);
        assertThat(document.getUploadedBy()).isEqualTo("alice");
        assertThat(document.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-01-02T03:04:05Z").toInstant());
        assertThat(document.getUpdatedAt()).isEqualTo(OffsetDateTime.parse("2026-01-03T04:05:06Z").toInstant());
    }

    @Test
    void appliesDefaultsForMissingOptionalColumns() {
        when(row.get("id", Long.class)).thenReturn(7L);

        var document = AiKbDocumentRepository.document(row);

        assertThat(document.getStatus()).isEqualTo(DocumentStatus.PENDING);
        assertThat(document.getChunkCount()).isZero();
        assertThat(document.getTokenCount()).isZero();
        assertThat(document.getVersion()).isEqualTo(1);
        assertThat(document.getCreatedAt()).isNull();
        assertThat(document.getUpdatedAt()).isNull();
    }
}
