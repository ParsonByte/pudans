package cn.tolim.pudans.core.live.resolve;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PacePayloadExtractor {

    private static final String MARKER = "self.__pace_f.push(";

    public JsonNode extract(String html) {
        if (html == null || html.isEmpty()) {
            throw LiveErrorCode.LIVE_RESOLVE_FAILED.exception();
        }
        var arguments = new ArrayList<String>();
        var from = 0;
        while (true) {
            var marker = html.indexOf(MARKER, from);
            if (marker < 0) {
                break;
            }
            var open = html.indexOf('(', marker);
            var end = scanBalanced(html, open);
            if (end < 0) {
                break;
            }
            var argument = html.substring(open + 1, end).trim();
            if (argument.startsWith("[")) {
                arguments.add(argument);
            }
            from = end + 1;
        }
        for (var index = arguments.size() - 1; index >= 0; index--) {
            var payload = parsePayload(arguments.get(index));
            if (payload != null) {
                return payload;
            }
        }
        throw LiveErrorCode.LIVE_RESOLVE_FAILED.exception();
    }

    private JsonNode parsePayload(String argument) {
        try {
            var array = JsonUtils.mapper().readTree(argument);
            if (!array.isArray() || array.size() < 2) {
                return null;
            }
            var encoded = array.get(1).asText(null);
            if (encoded == null || encoded.isEmpty()) {
                return null;
            }
            var colon = encoded.indexOf(':');
            var json = colon >= 0 ? encoded.substring(colon + 1) : encoded;
            var payload = JsonUtils.mapper().readTree(json);
            if (payload == null) {
                return null;
            }
            if (payload.isArray()) {
                for (var i = payload.size() - 1; i >= 0; i--) {
                    var candidate = payload.get(i);
                    if (candidate != null && candidate.isObject() && candidate.has("state")) {
                        return candidate;
                    }
                }
                return null;
            }
            return payload.isObject() && payload.has("state") ? payload : null;
        } catch (Exception error) {
            return null;
        }
    }

    private int scanBalanced(String html, int open) {
        if (open < 0) {
            return -1;
        }
        var depth = 0;
        var quote = 0;
        var escaped = false;
        for (var index = open; index < html.length(); index++) {
            var ch = html.charAt(index);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == quote) {
                    quote = 0;
                }
                continue;
            }
            if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == '(' || ch == '[' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == ']' || ch == '}') {
                depth--;
                if (depth == 0) {
                    return ch == ')' ? index : -1;
                }
            }
        }
        return -1;
    }
}
