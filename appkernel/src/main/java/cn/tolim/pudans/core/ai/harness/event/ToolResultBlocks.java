package cn.tolim.pudans.core.ai.harness.event;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.harness.agent.AgentRun;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

// 从工具输出中提取 data URL 或媒体 JSON，并发出 DATA_BLOCK_* 事件
public final class ToolResultBlocks {

    private static final Pattern DATA_URL =
            Pattern.compile("data:([\\w.+-]+/[\\w.+-]+);base64,([A-Za-z0-9+/=\\s]+)");

    private static final int MAX_SCAN_CHARS = 2 * 1024 * 1024;

    private static final int MAX_BLOCKS = 4;

    private ToolResultBlocks() {}

    public static void emit(AgentRun run, String output) {
        for (var block : extract(output)) {
            run.emit(new AgentEvent.DataBlockStart(run.id(), block.mediaType()));
            run.emit(new AgentEvent.DataBlockDelta(run.id(), block.mediaType(), block.data()));
            run.emit(new AgentEvent.DataBlockEnd(run.id(), block.mediaType()));
        }
    }

    static List<Block> extract(String output) {
        if (output == null || output.isBlank()) {
            return List.of();
        }
        var text = output.length() > MAX_SCAN_CHARS ? output.substring(0, MAX_SCAN_CHARS) : output;
        var blocks = new LinkedHashSet<Block>();
        var matcher = DATA_URL.matcher(text);
        while (matcher.find() && blocks.size() < MAX_BLOCKS) {
            blocks.add(new Block(matcher.group(1), matcher.group(2).strip()));
        }
        if (blocks.isEmpty() && text.stripLeading().startsWith("{")) {
            addJsonBlock(text, blocks);
        }
        return List.copyOf(blocks);
    }

    private static void addJsonBlock(String text, LinkedHashSet<Block> blocks) {
        try {
            JsonNode node = JsonUtils.mapper().readTree(text);
            var mediaType = textField(node, "mediaType", "mimeType");
            var data = textField(node, "data", "base64");
            if (!mediaType.isBlank() && !data.isBlank()) {
                blocks.add(new Block(mediaType, stripDataUrlPrefix(mediaType, data)));
            }
            var url = textField(node, "url");
            var urlMatcher = DATA_URL.matcher(url);
            if (urlMatcher.find()) {
                blocks.add(new Block(urlMatcher.group(1), urlMatcher.group(2).strip()));
            }
        } catch (Exception ignored) {
            // 不是结构化媒体输出时不发数据块事件
        }
    }

    private static String stripDataUrlPrefix(String mediaType, String data) {
        var matcher = DATA_URL.matcher(data);
        return matcher.find() ? matcher.group(2).strip() : data;
    }

    private static String textField(JsonNode node, String... names) {
        for (var name : names) {
            var value = node.get(name);
            if (value != null && value.isTextual() && !value.textValue().isBlank()) {
                return value.textValue();
            }
        }
        return "";
    }

    record Block(String mediaType, String data) {}
}