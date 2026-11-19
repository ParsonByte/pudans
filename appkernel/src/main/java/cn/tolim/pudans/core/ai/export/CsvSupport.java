package cn.tolim.pudans.core.ai.export;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

public final class CsvSupport {

    private CsvSupport() {}

    public static String line(@Nullable String... fields) {
        var builder = new StringBuilder();
        for (var index = 0; index < fields.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(escape(fields[index]));
        }
        return builder.append('\n').toString();
    }

    public static String escape(@Nullable String value) {
        if (value == null) {
            return "";
        }
        if (!requiresQuotes(value)) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    public static List<String> parseLine(String line) {
        var fields = new ArrayList<String>();
        var value = new StringBuilder();
        var quoted = false;
        for (var index = 0; index < line.length(); index++) {
            var current = line.charAt(index);
            if (quoted) {
                if (current == '"' && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    value.append('"');
                    index++;
                } else if (current == '"') {
                    quoted = false;
                } else {
                    value.append(current);
                }
            } else if (current == '"') {
                quoted = true;
            } else if (current == ',') {
                fields.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new IllegalArgumentException("CSV 引号未闭合。");
        }
        fields.add(value.toString());
        return fields;
    }

    private static boolean requiresQuotes(String value) {
        return value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
    }
}
