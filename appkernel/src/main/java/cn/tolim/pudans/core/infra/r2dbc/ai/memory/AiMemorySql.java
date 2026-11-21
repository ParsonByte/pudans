package cn.tolim.pudans.core.infra.r2dbc.ai.memory;

final class AiMemorySql {

    private AiMemorySql() {}

    // 转义 ILIKE 通配符，查询始终按普通文本匹配。
    static String likePattern(String query) {
        var escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}