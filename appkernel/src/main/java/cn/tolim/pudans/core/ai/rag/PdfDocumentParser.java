package cn.tolim.pudans.core.ai.rag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class PdfDocumentParser implements DocumentParser {

    private static final String MIME_TYPE = "application/pdf";

    private static final String PAGE_SEPARATOR = "\n\n";

    @Override
    public boolean supports(String fileName, @Nullable String mimeType) {
        return MIME_TYPE.equalsIgnoreCase(mimeType) || DocumentParser.hasExtension(fileName, ".pdf");
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("文档内容不能为空。");
        }
        try (var document = Loader.loadPDF(content)) {
            var stripper = new PDFTextStripper();
            var text = new StringBuilder();
            var pages = new ArrayList<ParsedDocument.Page>();
            var totalPages = document.getNumberOfPages();
            for (var page = 1; page <= totalPages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                var pageText = stripper.getText(document);
                pages.add(new ParsedDocument.Page(text.length(), page));
                text.append(pageText);
                if (page < totalPages) {
                    text.append(PAGE_SEPARATOR);
                }
            }
            return new ParsedDocument(text.toString(), List.of(), pages);
        } catch (IOException error) {
            throw new IllegalArgumentException("PDF 文档解析失败。", error);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("PDF 文档解析失败。", error);
        }
    }
}