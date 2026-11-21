package cn.tolim.pudans.core.infra.audit;

import cn.tolim.pudans.contract.audit.AuditRecord;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

final class AuditFingerprint {

    private AuditFingerprint() {}

    static String of(AuditRecord record) {
        var canonical = new StringBuilder();
        append(canonical, record.getTraceFlowId());
        append(canonical, record.getUid());
        append(canonical, record.getOperation());
        append(canonical, record.getResourceType());
        append(canonical, record.getResourceName());
        append(canonical, record.getRequestMethod());
        append(canonical, record.getRequestPath());
        append(canonical, record.getResult());
        append(canonical, canonicalContext(record.getContext()));
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用。", e);
        }
    }

    private static void append(StringBuilder canonical, String value) {
        canonical.append(value == null ? "" : value).append('\u0000');
    }

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
