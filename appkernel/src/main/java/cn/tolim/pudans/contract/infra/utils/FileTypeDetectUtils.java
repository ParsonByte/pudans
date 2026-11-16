package cn.tolim.pudans.contract.infra.utils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.apache.tika.parser.ParseContext;
import org.springframework.util.Assert;

@UtilityClass
public class FileTypeDetectUtils {

    private static final Detector detector = new DefaultDetector();


    public static String detectMimeType(InputStream inputStream, String name) throws IOException {
        Assert.notNull(name, "文档名称不能为 null");
        var metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, name);
        return doDetectMimeType(inputStream, metadata);
    }


    public static String detectMimeType(InputStream inputStream) throws IOException {
        return doDetectMimeType(inputStream, new Metadata());
    }

    private static String doDetectMimeType(InputStream inputStream, Metadata metadata) throws IOException {
        Assert.notNull(inputStream, "inputStream 不能为 null");
        try (var stream = (!inputStream.markSupported() ? new BufferedInputStream(inputStream) : inputStream)) {
            return detector.detect(TikaInputStream.get(stream), metadata, new ParseContext())
                    .toString();
        }
    }

    public static String detectFileExtension(String mimeType) throws MimeTypeException {
        MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
        return mimeTypes.forName(mimeType).getExtension();
    }

    public static List<String> detectFileExtensions(String mimeType) throws MimeTypeException {
        MimeTypes mimeTypes = MimeTypes.getDefaultMimeTypes();
        return mimeTypes.forName(mimeType).getExtensions();
    }


    public static String getFileExtension(String fileName) {
        Assert.notNull(fileName, "fileName 不能为 null");
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            return fileName.substring(lastDot).toLowerCase();
        }
        return "";
    }


    public boolean isValidExtensionForMime(String mimeType, String fileName) {
        Assert.notNull(mimeType, "mimeType 不能为 null");
        Assert.notNull(fileName, "fileName 不能为 null");
        String fileExtension = getFileExtension(fileName);
        try {
            List<String> detectedExtByMime = detectFileExtensions(mimeType);
            return detectedExtByMime.stream().anyMatch(ext -> ext.equalsIgnoreCase(fileExtension));
        } catch (MimeTypeException e) {
            return false;
        }
    }
}
