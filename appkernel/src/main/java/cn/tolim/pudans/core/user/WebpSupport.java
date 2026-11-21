package cn.tolim.pudans.core.user;

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

    // 按运行时探测结果初始化解码支持
    public WebpSupport() {
        this(hasWebpReader());
    }

    // 允许测试直接指定解码可用性
    WebpSupport(boolean available) {
        this.available = available;
    }

    // 检测是否注册了该格式的图片读取器
    private static boolean hasWebpReader() {
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
            return readers.hasNext();
        } catch (RuntimeException e) {
            return false;
        }
    }

    // 启动时提示缺失解码器的影响
    @PostConstruct
    void warnIfUnavailable() {
        if (!available) {
            log.warn("未安装 WebP ImageIO 解码器；WebP 头像上传将被拒绝。");
        }
    }

    // 返回本机是否支持该图片格式解码
    public boolean isAvailable() {
        return available;
    }
}
