package healthtech;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JsonCodec {
    static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Expected JSON object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    static Object read(String source) { return new Parser(source).parse(); }

    static String write(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            StringBuilder result = new StringBuilder("\"");
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> result.append("\\\"");
                    case '\\' -> result.append("\\\\");
                    case '\n' -> result.append("\\n");
                    case '\r' -> result.append("\\r");
                    case '\t' -> result.append("\\t");
                    default -> {
                        if (c < 32) result.append(String.format("\\u%04x", (int) c));
                        else result.append(c);
                    }
                }
            }
            return result.append('"').toString();
        }
        if (value instanceof Boolean || value instanceof Number) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> entries = new ArrayList<>();
            map.forEach((k, v) -> entries.add(write(String.valueOf(k)) + ":" + write(v)));
            return "{" + String.join(",", entries) + "}";
        }
        if (value instanceof List<?> list) {
            List<String> entries = new ArrayList<>();
            list.forEach(item -> entries.add(write(item)));
            return "[" + String.join(",", entries) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value");
    }

    private static final class Parser {
        private final String input;
        private int pos;
        Parser(String input) { this.input = input; }
        Object parse() {
            Object value = value();
            skip();
            if (pos != input.length()) throw new IllegalArgumentException("Trailing JSON content");
            return value;
        }
        void skip() { while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++; }
        char next() {
            if (pos >= input.length()) throw new IllegalArgumentException("Unexpected end of JSON");
            return input.charAt(pos++);
        }
        Object value() {
            skip();
            char c = next();
            if (c == '"') return string();
            if (c == '{') {
                Map<String, Object> map = new LinkedHashMap<>();
                skip();
                if (input.charAt(pos) == '}') { pos++; return map; }
                do {
                    skip();
                    if (next() != '"') throw new IllegalArgumentException("Expected key");
                    String key = string();
                    skip();
                    if (next() != ':') throw new IllegalArgumentException("Expected colon");
                    map.put(key, value());
                    skip();
                    c = next();
                } while (c == ',');
                if (c != '}') throw new IllegalArgumentException("Expected closing brace");
                return map;
            }
            if (c == '[') {
                List<Object> list = new ArrayList<>();
                skip();
                if (input.charAt(pos) == ']') { pos++; return list; }
                do {
                    list.add(value());
                    skip();
                    c = next();
                } while (c == ',');
                if (c != ']') throw new IllegalArgumentException("Expected closing bracket");
                return list;
            }
            pos--;
            if (input.startsWith("true", pos)) { pos += 4; return true; }
            if (input.startsWith("false", pos)) { pos += 5; return false; }
            if (input.startsWith("null", pos)) { pos += 4; return null; }
            int start = pos;
            while (pos < input.length() && "-+0123456789.eE".indexOf(input.charAt(pos)) >= 0) pos++;
            if (start == pos) throw new IllegalArgumentException("Invalid JSON value");
            return Double.parseDouble(input.substring(start, pos));
        }
        String string() {
            StringBuilder result = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return result.toString();
                if (c != '\\') { result.append(c); continue; }
                c = next();
                switch (c) {
                    case '"', '\\', '/' -> result.append(c);
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'u' -> {
                        if (pos + 4 > input.length()) throw new IllegalArgumentException("Invalid unicode escape");
                        result.append((char) Integer.parseInt(input.substring(pos, pos + 4), 16));
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Invalid escape");
                }
            }
        }
    }
}
