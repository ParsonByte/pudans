package cn.tolim.pudans.core.ai.rag;

import cn.tolim.pudans.contract.ai.KnowledgeBase;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Chunking {

    private static final String HEADING_SEPARATOR = " > ";

    private static final List<String> SEPARATORS =
            List.of("\n\n", "\n", "。", "！", "？", "；", ". ", "! ", "? ", "; ", " ");

    public List<KnowledgeBaseChunk> chunk(
            ParsedDocument document, KnowledgeBase.ChunkStrategy strategy, int chunkSize, int chunkOverlap) {
        if (document == null) {
            throw new IllegalArgumentException("解析文本不能为空。");
        }
        if (strategy == null) {
            throw new IllegalArgumentException("切片策略不能为空。");
        }
        if (chunkSize < 1) {
            throw new IllegalArgumentException("切片大小必须大于 0。");
        }
        if (chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("切片重叠必须大于等于 0 且小于切片大小。");
        }
        if (strategy == KnowledgeBase.ChunkStrategy.SEMANTIC) {
            throw new IllegalArgumentException("暂不支持语义切片。");
        }
        var text = document.text();
        if (text.isBlank()) {
            return List.of();
        }
        var ranges = strategy == KnowledgeBase.ChunkStrategy.FIXED
                ? fixedRanges(text.length(), chunkSize, chunkOverlap)
                : recursiveRanges(document, chunkSize, chunkOverlap);
        var chunks = new ArrayList<KnowledgeBaseChunk>();
        for (var range : ranges) {
            var trimmed = trim(text, range);
            if (trimmed.isEmpty()) {
                continue;
            }
            var content = text.substring(trimmed.start(), trimmed.end());
            var chunk = new KnowledgeBaseChunk();
            chunk.setChunkIndex(chunks.size());
            chunk.setContent(content);
            chunk.setTokenCount(estimateTokenCount(content));
            chunk.setPageNo(pageNoAt(document.pages(), trimmed.start()));
            chunk.setHeadingPath(headingPathAt(document.headings(), trimmed.start()));
            chunks.add(chunk);
        }
        return List.copyOf(chunks);
    }

    private static List<Range> recursiveRanges(ParsedDocument document, int chunkSize, int chunkOverlap) {
        var text = document.text();
        var ranges = new ArrayList<Range>();
        var sectionStart = 0;
        for (var heading : document.headings()) {
            if (heading.offset() > sectionStart) {
                ranges.addAll(recursiveSection(text, sectionStart, heading.offset(), chunkSize, chunkOverlap));
            }
            sectionStart = heading.offset();
        }
        if (sectionStart < text.length()) {
            ranges.addAll(recursiveSection(text, sectionStart, text.length(), chunkSize, chunkOverlap));
        }
        return ranges;
    }

    private static List<Range> recursiveSection(
            String text, int start, int end, int chunkSize, int chunkOverlap) {
        if (end <= start) {
            return List.of();
        }
        var pieces = splitRanges(text, start, end, chunkSize, 0);
        return mergeRanges(pieces, chunkSize, chunkOverlap);
    }

    private static List<Range> fixedRanges(int length, int chunkSize, int chunkOverlap) {
        var ranges = new ArrayList<Range>();
        var step = chunkSize - chunkOverlap;
        for (var start = 0; start < length; start += step) {
            ranges.add(new Range(start, Math.min(start + chunkSize, length)));
            if (start + chunkSize >= length) {
                break;
            }
        }
        return ranges;
    }

    private static List<Range> splitRanges(String text, int start, int end, int chunkSize, int separatorIndex) {
        if (end - start <= chunkSize) {
            return List.of(new Range(start, end));
        }
        if (separatorIndex >= SEPARATORS.size()) {
            return hardSplit(start, end, chunkSize);
        }
        var parts = splitBySeparator(text, start, end, SEPARATORS.get(separatorIndex));
        var ranges = new ArrayList<Range>();
        for (var part : parts) {
            if (part.length() <= chunkSize) {
                ranges.add(part);
            } else {
                ranges.addAll(splitRanges(text, part.start(), part.end(), chunkSize, separatorIndex + 1));
            }
        }
        return ranges;
    }

    private static List<Range> splitBySeparator(String text, int start, int end, String separator) {
        var parts = new ArrayList<Range>();
        var position = start;
        while (position < end) {
            var index = text.indexOf(separator, position);
            if (index < 0 || index + separator.length() > end) {
                break;
            }
            parts.add(new Range(position, index + separator.length()));
            position = index + separator.length();
        }
        if (position < end || parts.isEmpty()) {
            parts.add(new Range(position, end));
        }
        return parts;
    }

    private static List<Range> hardSplit(int start, int end, int chunkSize) {
        var ranges = new ArrayList<Range>();
        for (var position = start; position < end; position += chunkSize) {
            ranges.add(new Range(position, Math.min(position + chunkSize, end)));
        }
        return ranges;
    }

    private static List<Range> mergeRanges(List<Range> pieces, int chunkSize, int chunkOverlap) {
        if (pieces.isEmpty()) {
            return List.of();
        }
        var ranges = new ArrayList<Range>();
        var currentStart = pieces.get(0).start();
        var currentEnd = pieces.get(0).end();
        for (var index = 1; index < pieces.size(); index++) {
            var piece = pieces.get(index);
            if (currentEnd > currentStart && piece.end() - currentStart > chunkSize) {
                ranges.add(new Range(currentStart, currentEnd));
                var nextStart = Math.max(currentEnd - chunkOverlap, currentStart + 1);
                if (piece.end() - nextStart > chunkSize) {
                    nextStart = Math.max(piece.start(), piece.end() - chunkSize);
                }
                currentStart = nextStart;
                currentEnd = Math.max(piece.end(), currentStart);
            } else {
                currentEnd = piece.end();
            }
        }
        if (currentEnd > currentStart) {
            ranges.add(new Range(currentStart, currentEnd));
        }
        return ranges;
    }

    private static Range trim(String text, Range range) {
        var start = range.start();
        var end = range.end();
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return new Range(start, end);
    }

    private static Integer pageNoAt(List<ParsedDocument.Page> pages, int offset) {
        Integer pageNo = null;
        for (var page : pages) {
            if (page.offset() > offset) {
                break;
            }
            pageNo = page.pageNo();
        }
        return pageNo;
    }

    private static String headingPathAt(List<ParsedDocument.Heading> headings, int offset) {
        Deque<ParsedDocument.Heading> stack = new ArrayDeque<>();
        for (var heading : headings) {
            if (heading.offset() > offset) {
                break;
            }
            while (!stack.isEmpty() && stack.peek().level() >= heading.level()) {
                stack.pop();
            }
            stack.push(heading);
        }
        if (stack.isEmpty()) {
            return null;
        }
        var titles = new ArrayList<String>(stack.size());
        for (var heading : stack) {
            titles.add(heading.title());
        }
        Collections.reverse(titles);
        return String.join(HEADING_SEPARATOR, titles);
    }

    private static int estimateTokenCount(String content) {
        var tokens = 0;
        var inAsciiWord = false;
        for (var index = 0; index < content.length(); ) {
            var codePoint = content.codePointAt(index);
            index += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                inAsciiWord = false;
                continue;
            }
            var script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                    || script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HANGUL) {
                tokens++;
                inAsciiWord = false;
            } else if (codePoint < 128 && Character.isLetterOrDigit(codePoint)) {
                if (!inAsciiWord) {
                    tokens++;
                    inAsciiWord = true;
                }
            } else {
                tokens++;
                inAsciiWord = false;
            }
        }
        return tokens;
    }

    private record Range(int start, int end) {

        private int length() {
            return end - start;
        }

        private boolean isEmpty() {
            return start >= end;
        }
    }
}
