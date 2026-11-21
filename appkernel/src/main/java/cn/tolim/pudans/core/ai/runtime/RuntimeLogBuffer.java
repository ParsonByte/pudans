package cn.tolim.pudans.core.ai.runtime;

import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxLogEvent;
import cn.tolim.pudans.infra.properties.AiProperties;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class RuntimeLogBuffer {

    private final int capacity;

    private final ConcurrentMap<String, Deque<SandboxLogEvent>> buffers = new ConcurrentHashMap<>();

    public RuntimeLogBuffer(AiProperties properties) {
        var configured = properties.getRuntime().getLogBufferSize();
        capacity = configured <= 0 ? 500 : configured;
    }

    public void append(String agentName, SandboxLogEvent event) {
        append(agentName, List.of(event));
    }

    public void append(String agentName, List<SandboxLogEvent> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        var buffer = buffers.computeIfAbsent(agentName, key -> new ArrayDeque<>(capacity));
        synchronized (buffer) {
            for (var event : events) {
                if (buffer.size() >= capacity) {
                    buffer.removeFirst();
                }
                buffer.addLast(event);
            }
        }
    }

    public void appendOutput(String agentName, String stream, String output) {
        if (output == null || output.isEmpty()) {
            return;
        }
        var events = new ArrayList<SandboxLogEvent>();
        var lines = output.split("\\R", -1);
        var limit = lines.length;
        if (limit > 0 && lines[limit - 1].isEmpty()) {
            limit--;
        }
        var now = Instant.now();
        for (var index = 0; index < limit; index++) {
            events.add(new SandboxLogEvent(now, stream, lines[index]));
        }
        append(agentName, events);
    }

    public List<SandboxLogEvent> snapshot(String agentName) {
        var buffer = buffers.get(agentName);
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer) {
            return List.copyOf(buffer);
        }
    }

    public void remove(String agentName) {
        buffers.remove(agentName);
    }
}