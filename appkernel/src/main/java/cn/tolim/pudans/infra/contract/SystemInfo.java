package cn.tolim.pudans.infra.contract;

import com.github.zafarkhaja.semver.Version;
import java.net.URL;
import java.util.TimeZone;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SystemInfo {
    private String title;

    private String subtitle;

    private String logo;

    private String favicon;

    private String siteId;

    private URL url;

    private Version version;

    private SeoProp seo;

    private TimeZone timeZone;


    @Data
    @Accessors(chain = true)
    public static class SeoProp {
        private boolean blockSpiders;
        private String keywords;
        private String description;
    }
}
