package dev.remod.common.json;

import java.util.List;
import java.util.Map;

/** Serialises the {@link Json} document model back to text. */
final class JsonWriter {

    private static final String INDENT = "  ";

    private JsonWriter() {
    }

    static void write(Object value, StringBuilder out, boolean pretty, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof JsonObject) {
            writeObject(((JsonObject) value).toMap(), out, pretty, depth);
        } else if (value instanceof Map) {
            writeObject(asStringKeyed(value), out, pretty, depth);
        } else if (value instanceof JsonArray) {
            writeArray(((JsonArray) value).toList(), out, pretty, depth);
        } else if (value instanceof List) {
            writeArray((List<?>) value, out, pretty, depth);
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Number) {
            writeNumber((Number) value, out);
        } else if (value instanceof Enum) {
            writeString(((Enum<?>) value).name(), out);
        } else {
            // Anything else is rendered as its string form rather than crashing a
            // write path that is often the last step of an install.
            writeString(String.valueOf(value), out);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asStringKeyed(Object value) {
        return (Map<String, Object>) value;
    }

    private static void writeObject(Map<String, Object> map, StringBuilder out,
                                    boolean pretty, int depth) {
        if (map.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newlineAndIndent(out, pretty, depth + 1);
            writeString(entry.getKey(), out);
            out.append(':');
            if (pretty) {
                out.append(' ');
            }
            write(entry.getValue(), out, pretty, depth + 1);
        }
        newlineAndIndent(out, pretty, depth);
        out.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out, boolean pretty, int depth) {
        if (list.isEmpty()) {
            out.append("[]");
            return;
        }
        out.append('[');
        boolean first = true;
        for (Object element : list) {
            if (!first) {
                out.append(',');
            }
            first = false;
            newlineAndIndent(out, pretty, depth + 1);
            write(element, out, pretty, depth + 1);
        }
        newlineAndIndent(out, pretty, depth);
        out.append(']');
    }

    private static void writeNumber(Number number, StringBuilder out) {
        if (number instanceof Double || number instanceof Float) {
            double d = number.doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                // Neither is representable in JSON; null is the conventional stand-in.
                out.append("null");
                return;
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                out.append((long) d);
                return;
            }
        }
        out.append(number);
    }

    private static void newlineAndIndent(StringBuilder out, boolean pretty, int depth) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        for (int i = 0; i < depth; i++) {
            out.append(INDENT);
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b");  break;
                case '\f': out.append("\\f");  break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }
}
