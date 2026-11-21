package cn.tolim.pudans.infra.audit;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
import cn.tolim.pudans.infra.utils.Digests;
import java.util.Map;
import java.util.TreeMap;

final class AuditFingerprint {

    private AuditFingerprint() {}

    // 生成覆盖请求来源与内容的去重指纹，防止重复审计。
    static String of(AuditRecord record) {
        var canonical = new StringBuilder();
        // 服务端生成的请求标识、时间、来源必须参与指纹，避免客户端复用请求头抑制审计。
        append(canonical, record.getTraceId());
        append(canonical, record.getTimestamp() == null ? null : record.getTimestamp().toString());
        append(canonical, record.getIp());
        append(canonical, record.getUserAgent());
        append(canonical, record.getTraceFlowId());
        append(canonical, record.getUid());
        append(canonical, record.getOperation());
        append(canonical, record.getResourceType());
        append(canonical, record.getResourceName());
        append(canonical, record.getRequestMethod());
        append(canonical, record.getRequestPath());
        append(canonical, record.getResult());
        append(canonical, canonicalContext(record.getContext()));
        return Digests.sha256Hex(canonical.toString());
    }

    // 使用空字符分隔字段，避免拼接产生歧义。
    private static void append(StringBuilder canonical, String value) {
        canonical.append(value == null ? "" : value).append('\u0000');
    }

    // 去除易变字段并按键排序，保证同一上下文生成稳定指纹。
    private static String canonicalContext(Map<String, Object> context) {
        if (context == null || context.isEmpty()) {
            return "";
        }
        var normalized = new TreeMap<String, Object>();
        context.forEach((key, value) -> {
            if (value == null || "timestamp".equals(key) || "requestId".equals(key) || "durationMs".equals(key)) {
                return;
            }
            normalized.put(key, value);
        });
        return JsonUtils.objectToJson(normalized);
    }
}
