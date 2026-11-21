package cn.tolim.pudans.core.live.recorder;

import cn.tolim.pudans.contract.live.LiveRecording;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// 成品清单用于崩溃恢复，写入失败只告警不阻断上传
@Slf4j
@Component
@RequiredArgsConstructor
public class LivePartManifest {

    private final ObjectMapper mapper;

    private final LiveRecordingWorkspace workspace;

    public void append(String recordingName, LiveRecording.Part part) {
        try {
            LiveSegmentManifest.appendLine(
                    workspace.partsManifest(recordingName), mapper.writeValueAsString(part));
        } catch (IOException error) {
            log.warn("序列化成品清单失败 [{}#{}]：{}", recordingName, part.getIndex(), error.getMessage());
        } catch (IllegalStateException error) {
            log.warn("写入成品清单失败 [{}#{}]：{}", recordingName, part.getIndex(), error.getMessage());
        }
    }
}
