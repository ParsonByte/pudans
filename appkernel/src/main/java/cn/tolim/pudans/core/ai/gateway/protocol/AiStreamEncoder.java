package cn.tolim.pudans.core.ai.gateway.protocol;

import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;

public interface AiStreamEncoder {

    String start();

    String event(AiStreamEvent event);

    String finish(AiStreamEvent event);
}
