package cn.tolim.pudans.core.ai.rag;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Order(3)
public class WordDocumentParser implements DocumentParser {

    private static final String MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private static final Pattern HEADING_STYLE = Pattern.compile("(?i)^(?:heading|标题)\\s*([1-6])$");

    private static final String CELL_LINE_BREAK = "<br>";

    @Override
    public boolean supports(String fileName, @Nullable String mimeType) {
        return MIME_TYPE.equalsIgnoreCase(mimeType) || DocumentParser.hasExtension(fileName, ".docx");
    }

    @Override
    public ParsedDocument parse(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("文档内容不能为空。");
        }
        try (var document = new XWPFDocument(new ByteArrayInputStream(content))) {
            var text = new StringBuilder();
            var headings = new ArrayList<ParsedDocument.Heading>();
            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    appendParagraph(document, text, headings, paragraph);
                } else if (element instanceof XWPFTable table) {
                    appendTable(text, table);
                }
            }
            return new ParsedDocument(text.toString(), headings, List.of());
        } catch (Exception error) {
            throw new IllegalArgumentException("Word 文档解析失败。", error);
        }
    }

    private static void appendParagraph(
            XWPFDocument document,
            StringBuilder text,
            List<ParsedDocument.Heading> headings,
            XWPFParagraph paragraph) {
        var value = paragraph.getText();
        if (!StringUtils.hasText(value)) {
            return;
        }
        var stripped = value.strip();
        var level = headingLevel(document, paragraph);
        if (level > 0) {
            headings.add(new ParsedDocument.Heading(text.length(), level, stripped));
        }
        text.append(stripped).append('\n');
    }

    private static int headingLevel(XWPFDocument document, XWPFParagraph paragraph) {
        var styleId = paragraph.getStyle();
        var level = headingLevel(styleId);
        if (level > 0 || styleId == null || document.getStyles() == null) {
            return level;
        }
        var style = document.getStyles().getStyle(styleId);
        return style == null ? 0 : headingLevel(style.getName());
    }

    private static int headingLevel(@Nullable String style) {
        if (!StringUtils.hasText(style)) {
            return 0;
        }
        var matcher = HEADING_STYLE.matcher(style.strip());
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static void appendTable(StringBuilder text, XWPFTable table) {
        var rows = new ArrayList<List<String>>();
        for (var row : table.getRows()) {
            var cells = new ArrayList<String>();
            for (var cell : row.getTableCells()) {
                cells.add(tableCell(cell.getText()));
            }
            if (!cells.isEmpty()) {
                rows.add(List.copyOf(cells));
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        var columns = rows.stream().mapToInt(List::size).max().orElse(0);
        appendTableRow(text, rows.get(0), columns);
        text.append('|');
        for (var column = 0; column < columns; column++) {
            text.append(" --- |");
        }
        text.append('\n');
        for (var index = 1; index < rows.size(); index++) {
            appendTableRow(text, rows.get(index), columns);
        }
        text.append('\n');
    }

    private static void appendTableRow(StringBuilder text, List<String> cells, int columns) {
        text.append('|');
        for (var column = 0; column < columns; column++) {
            var cell = column < cells.size() ? cells.get(column) : "";
            text.append(' ').append(cell).append(" |");
        }
        text.append('\n');
    }

    private static String tableCell(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.strip()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\n", CELL_LINE_BREAK);
    }
}