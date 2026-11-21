package cn.tolim.pudans.contract.live;

import java.util.List;

public record LiveRecordingDownload(String recording, List<PartDownload> parts) {}
