package cn.tolim.pudans.contract.live;

import cn.tolim.pudans.contract.extension.AbstractExtension;
import cn.tolim.pudans.contract.extension.GVK;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@GVK(
        group = LiveRecording.GROUP,
        version = LiveRecording.VERSION,
        kind = LiveRecording.KIND,
        plural = "liverecordings",
        singular = "liverecording")
public class LiveRecording extends AbstractExtension {

    public static final String GROUP = "pudans.tolim.cn";
    public static final String VERSION = "v1alpha1";
    public static final String KIND = "LiveRecording";

    private Spec spec = new Spec();

    private Status status = new Status();

    @Data
    public static class Spec {

        private String sourceName;

        private LivePlatform platform;

        private String roomId;

        private String roomTitle;

        private String anchorName;

        private LiveRecordingFormat format = LiveRecordingFormat.TS;

        private Instant startedAt;

        private boolean auto;

        private String requestedBy;

        private String storagePolicy;

        private int segmentSeconds = 30;

        private int mergeIntervalSeconds = 3600;

        private String retryOf;
    }

    @Data
    public static class Status {

        private LiveRecordingState state = LiveRecordingState.QUEUED;

        private String message;

        private String errorCode;

        private Instant startedAt;

        private Instant endedAt;

        private Instant lastPacketAt;

        private Instant lastWriteAt;

        private Instant heartbeatAt;

        private String ownerInstance;

        private Instant stopRequestedAt;

        private long durationMillis;

        private long totalBytes;

        private int segmentCount;

        private int partCount;

        private List<Part> parts = new ArrayList<>();

        private long localBytes;

        private int attempts;

        private int resolveAttempts;

        private int cleanupAttempts;
    }

    @Data
    public static class Part {

        private int index;

        private String filename;

        private PartState state = PartState.MERGING;

        private Instant startedAt;

        private Instant endedAt;

        private long durationMillis;

        private long size;

        private int segmentCount;

        private String storageFile;

        private String errorCode;

        private String message;
    }
}
