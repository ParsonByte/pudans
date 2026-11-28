package cn.tolim.pudans.communal.mybatis.sql;

import cn.tolim.pudans.communal.mybatis.page.PageQuery;

import java.util.UUID;
import java.util.regex.Pattern;

// PostgreSQL specific SQL helpers; no other database dialect is supported.
public final class PostgresDialect {

    private static final Pattern IDENTIFIER_PART = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String COUNT_ALIAS = "pudans_count";

    private PostgresDialect() {
    }

    // Quotes a table or column name, supporting a dotted schema qualified identifier.
    public static String quoteIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        String[] parts = identifier.split("\\.", -1);
        StringBuilder quoted = new StringBuilder(identifier.length() + parts.length * 2);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (!IDENTIFIER_PART.matcher(part).matches()) {
                throw new IllegalArgumentException("invalid identifier: " + identifier);
            }
            if (index > 0) {
                quoted.append('.');
            }
            quoted.append('"').append(part).append('"');
        }
        return quoted.toString();
    }

    // Appends LIMIT/OFFSET; both values are validated integers and safe to inline.
    public static String limitOffset(PageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("query must not be null");
        }
        return "LIMIT " + query.limit() + " OFFSET " + query.offset();
    }

    public static String pageSql(String selectSql, PageQuery query) {
        if (selectSql == null || selectSql.isBlank()) {
            throw new IllegalArgumentException("selectSql must not be blank");
        }
        return stripTrailingSemicolon(selectSql) + " " + limitOffset(query);
    }

    // Wraps a select statement in a COUNT query.
    public static String countSql(String selectSql) {
        if (selectSql == null || selectSql.isBlank()) {
            throw new IllegalArgumentException("selectSql must not be blank");
        }
        return "SELECT COUNT(*) FROM (" + stripTrailingSemicolon(selectSql) + ") " + COUNT_ALIAS;
    }

    // Escapes LIKE wildcards so user input is matched literally.
    public static String escapeLike(String value) {
        if (value == null) {
            return null;
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '%' || character == '_') {
                escaped.append('\\');
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    // Builds a contains pattern with escaped wildcards.
    public static String containsPattern(String value) {
        return value == null ? null : "%" + escapeLike(value) + "%";
    }

    // Builds a validated PostgreSQL uuid literal.
    public static String uuidLiteral(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("uuid must not be blank");
        }
        return "'" + UUID.fromString(value).toString() + "'::uuid";
    }

    private static String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}