package cn.tolim.pudans.core.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class CitationAssemblerTest {

    private final CitationAssembler assembler = new CitationAssembler();

    @Test
    void numbersCitationsInInjectedOrder() {
        var chunks = List.of(
                result(11, 101, "产品手册.pdf", 12, "第三章 > 3.2 安装步骤", 0.82, "安装前请备份。"),
                result(12, 102, "运维规范.md", null, "2.1 发布流程", 0.75, "发布前需审批。"));

        var assembled = assembler.assemble(chunks);

        assertThat(assembled.citations()).hasSize(2);
        assertThat(assembled.citations()).extracting(Citation::index).containsExactly(1, 2);
        assertThat(assembled.citations().get(0))
                .extracting(
                        Citation::docId,
                        Citation::fileName,
                        Citation::pageNo,
                        Citation::headingPath,
                        Citation::score,
                        Citation::snippet)
                .containsExactly(
                        101L, "产品手册.pdf", 12, "第三章 > 3.2 安装步骤", 0.82, "安装前请备份。");
        assertThat(assembled.prompt())
                .contains("【知识库资料】")
                .contains("[1]《产品手册.pdf》第 12 页 · 第三章 > 3.2 安装步骤")
                .contains("安装前请备份。")
                .contains("[2]《运维规范.md》· 2.1 发布流程")
                .contains("发布前需审批。")
                .doesNotContain("null");
    }

    @Test
    void omitsPageAndHeadingWhenTheyAreAbsent() {
        var assembled = assembler.assemble(
                List.of(result(11, 101, "readme.txt", null, null, 0.5, "纯文本内容")));

        assertThat(assembled.prompt()).contains("[1]《readme.txt》");
        assertThat(assembled.citations()).singleElement().satisfies(citation -> {
            assertThat(citation.pageNo()).isNull();
            assertThat(citation.headingPath()).isNull();
        });
    }

    @Test
    void returnsEmptyPromptWhenNoChunksAreInjected() {
        var assembled = assembler.assemble(List.of());

        assertThat(assembled.prompt()).isEmpty();
        assertThat(assembled.citations()).isEmpty();
        assertThat(assembler.assemble(null).citations()).isEmpty();
    }

    @Test
    void rejectsCitationsWithoutAValidIndex() {
        assertThatThrownBy(() -> new Citation(0, 101, "readme.txt", null, null, 0.5, "内容"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RetrievedChunk result(
            long chunkId,
            long documentId,
            String fileName,
            Integer pageNo,
            String headingPath,
            double score,
            String content) {
        var candidate = new RetrievalCandidate(
                chunkId, documentId, "kb-a", fileName, content, pageNo, headingPath, score);
        return new RetrievedChunk(candidate, score);
    }
}
