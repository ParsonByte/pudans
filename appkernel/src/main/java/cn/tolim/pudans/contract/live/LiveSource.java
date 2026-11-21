package cn.tolim.pudans.contract.live;

import cn.tolim.pudans.extension.api.AbstractExtension;
import cn.tolim.pudans.extension.api.GVK;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = LiveSource.GROUP,
        version = LiveSource.VERSION,
        kind = LiveSource.KIND,
        plural = "livesources",
        singular = "livesource")
public class LiveSource extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "LiveSource";

    private Spec spec = new Spec();

    private Status status = new Status();

    @Data
    public static class Spec {

        private LivePlatform platform = LivePlatform.DOUYIN;

        private String roomId;

        private String roomUrl;

        private String alias;

        private boolean enabled = true;

        private boolean autoRecord = true;

        private LiveRecordingFormat recordingFormat = LiveRecordingFormat.TS;

        private int segmentSeconds = 30;

        private int mergeIntervalSeconds = 3600;

        private int checkIntervalSeconds = 60;

        private String storagePolicy;
    }

    @Data
    public static class Status {

        private LiveSourceState state = LiveSourceState.IDLE;

        private String message;

        private String roomTitle;

        private String anchorName;

        private String anchorAvatar;

        private String anchorId;

        private boolean live;

        private String liveStatus;

        private Instant lastResolvedAt;

        private Instant lastLiveAt;

        private Instant lastOfflineAt;

        private Instant lastStreamAt;

        private Instant nextCheckAt;

        private Instant cooldownUntil;

        private int resolveFailures;

        private boolean rateLimited;

        private String lastResolveCode;

        private String lastResolveError;

        private String currentRecording;
    }
}
