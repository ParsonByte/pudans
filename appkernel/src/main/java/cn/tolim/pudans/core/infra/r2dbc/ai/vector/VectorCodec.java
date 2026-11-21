package cn.tolim.pudans.core.infra.r2dbc.ai.vector;

import java.util.Arrays;

public final class VectorCodec {

    private VectorCodec() {}

    public static String format(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("向量不能为 null。");
        }
        var builder = new StringBuilder(vector.length * 12 + 2);
        builder.append('[');
        for (var i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        builder.append(']');
        return builder.toString();
    }

    public static float[] parse(String value) {
        if (value == null || value.isBlank()) {
            return new float[0];
        }
        var text = value.trim();
        if (!text.startsWith("[") || !text.endsWith("]")) {
            throw new IllegalArgumentException("向量格式必须为 [v1,v2,...]。");
        }
        var body = text.substring(1, text.length() - 1).trim();
        if (body.isEmpty()) {
            return new float[0];
        }
        return Arrays.stream(body.split(","))
                .map(String::trim)
                .map(Float::parseFloat)
                .collect(
                        () -> new FloatList(),
                        FloatList::add,
                        FloatList::addAll)
                .toArray();
    }

    public static float[] requireDimension(float[] vector, int dimension) {
        if (vector == null || vector.length != dimension) {
            throw new IllegalArgumentException(
                    "向量维度不一致：期望 " + dimension + "，实际 "
                            + (vector == null ? 0 : vector.length) + "。");
        }
        return vector;
    }

    private static final class FloatList {

        private float[] values = new float[16];

        private int size;

        private void add(float value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        private void addAll(FloatList other) {
            for (var i = 0; i < other.size; i++) {
                add(other.values[i]);
            }
        }

        private float[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
