package cn.tolim.pudans.core.user.service;

import jakarta.annotation.PostConstruct;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebpSupport {

    private final boolean available;

    public WebpSupport() {
        this(hasWebpReader());
    }

    WebpSupport(boolean available) {
        this.available = available;
    }

    private static boolean hasWebpReader() {
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
            return readers.hasNext();
        } catch (RuntimeException e) {
            return false;
        }
    }

    @PostConstruct
    void warnIfUnavailable() {
        if (!available) {
            log.warn("未安装 WebP ImageIO 解码器；WebP 头像上传将被拒绝。");
        }
    }

    public boolean isAvailable() {
        return available;
    }
}
