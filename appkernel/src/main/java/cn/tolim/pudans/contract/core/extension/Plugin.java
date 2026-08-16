package cn.tolim.pudans.contract.core.extension;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import cn.tolim.pudans.contract.infra.ConditionList;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.Nullable;
import org.pf4j.PluginState;
import org.springframework.util.Assert;


@Data
@ToString(callSuper = true)
@GVK(group = "plugin.pudans.tolim.cn", version = "v1alpha1", kind = "Plugin", plural = "plugins", singular = "plugin")
@EqualsAndHashCode(callSuper = true)
public class Plugin extends AbstractExtension {

    public static final String SYSTEM_RESERVED_LABEL_KEY = "plugin.pudans.tolim.cn/system-reserved";

    public static final String BUILT_IN_KEEPER_FINALIZER = "plugin.pudans.tolim.cn/built-in-keeper";


    @Schema(requiredMode = REQUIRED)
    private PluginSpec spec;


    private @Nullable PluginStatus status;


    @JsonIgnore
    public PluginStatus statusNonNull() {
        if (this.status == null) {
            this.status = new PluginStatus();
        }
        return status;
    }


    public enum Phase {
        PENDING,
        STARTING,
        CREATED,
        DISABLING,
        DISABLED,
        RESOLVED,
        STARTED,
        STOPPED,
        FAILED,
        UNKNOWN,
        ;
    }

    @Data
    public static class PluginSpec {


        private String displayName;


        @Schema(
                requiredMode = REQUIRED,
                pattern = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-("
                        + "(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\."
                        + "(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\"
                        + ".[0-9a-zA-Z-]+)*))?$")
        private String version;


        private PluginAuthor author;


        private String logo;


        private Map<String, String> pluginDependencies = new HashMap<>(4);


        private String homepage;


        private String repo;


        private String issues;


        private String description;


        private List<License> license;


        private String requires = "*";


        private Boolean enabled = false;


        private String settingName;


        private String configMapName;
    }

    @Data
    public static class License {

        private String name;


        private String url;
    }

    @Data
    public static class PluginStatus {


        private Phase phase;


        private ConditionList conditions;


        private Instant lastStartTime;


        private PluginState lastProbeState;


        private String entry;


        private String stylesheet;


        private String logo;


        private URI loadLocation;

        public static ConditionList nullSafeConditions(PluginStatus status) {
            Assert.notNull(status, "The status must not be null.");
            if (status.getConditions() == null) {
                status.setConditions(new ConditionList());
            }
            return status.getConditions();
        }
    }

    @Data
    @ToString
    public static class PluginAuthor {


        @Schema(requiredMode = REQUIRED, minLength = 1)
        private String name;


        private String website;
    }
}
