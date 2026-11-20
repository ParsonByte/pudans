package cn.tolim.pudans.core.ai.rag;

import java.io.IOException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.SAXException;

final class TikaTextExtractor {

    private static final int UNLIMITED_CHARACTERS = -1;

    private TikaTextExtractor() {}

    static String extract(byte[] content) {
        if (content == null) {
            throw new IllegalArgumentException("文档内容不能为空。");
        }
        var handler = new BodyContentHandler(UNLIMITED_CHARACTERS);
        try (var stream = TikaInputStream.get(content)) {
            new AutoDetectParser().parse(stream, handler, new Metadata(), new ParseContext());
        } catch (IOException | SAXException | TikaException error) {
            throw new IllegalArgumentException("文档解析失败。", error);
        }
        return handler.toString();
    }
}