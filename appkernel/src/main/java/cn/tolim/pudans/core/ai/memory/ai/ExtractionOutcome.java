package cn.tolim.pudans.core.ai.memory.ai;

import cn.tolim.pudans.core.ai.memory.SceneSegment;
import java.util.List;

public record ExtractionOutcome(List<SceneSegment> scenes, String modelCode) {}
