package dev.remod.common.json;

/**
 * A recursive-descent JSON parser for the subset of RFC 8259 that ReMod needs.
 *
 * <p>Strict by design: trailing commas, comments and unquoted keys are
 * rejected, because silently accepting them would let a broken mod manifest
 * through and surface as a confusing failure much later.</p>
 */
final class JsonParser {

    /** Guards against stack overflow from a maliciously deeply nested document. */
    private static final int MAX_DEPTH = 128;

    private final String input;
    private int pos;

    JsonParser(String input) {
        if (input == null) {
            throw new JsonException("JSON input is null");
        }
        // A UTF-8 BOM is common in files edited on Windows; skip it rather than fail.
        this.input = input.startsWith("﻿") ? input.substring(1) : input;
    }

    Object parseDocument() {
        skipWhitespace();
        Object value = parseValue(0);
        skipWhitespace();
        if (pos < input.length()) {
            throw error("Unexpected trailing content");
        }
        return value;
    }

    private Object parseValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("JSON nested more than " + MAX_DEPTH + " levels deep");
        }
        if (pos >= input.length()) {
            throw error("Unexpected end of input");
        }
        char c = input.charAt(pos);
        switch (c) {
            case '{':
                return parseObject(depth);
            case '[':
                return parseArray(depth);
            case '"':
                return parseString();
            case 't':
                expectLiteral("true");
                return Boolean.TRUE;
            case 'f':
                expectLiteral("false");
                return Boolean.FALSE;
            case 'n':
                expectLiteral("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return parseNumber();
                }
                throw error("Unexpected character '" + c + "'");
        }
    }

    private JsonObject parseObject(int depth) {
        expect('{');
        JsonObject object = new JsonObject();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return object;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Object keys must be double-quoted strings");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            object.put(key, parseValue(depth + 1));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                skipWhitespace();
                if (peek() == '}') {
                    throw error("Trailing comma in object");
                }
                continue;
            }
            if (c == '}') {
                pos++;
                return object;
            }
            throw error("Expected ',' or '}' in object");
        }
    }

    private JsonArray parseArray(int depth) {
        expect('[');
        JsonArray array = new JsonArray();
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return array;
        }
        while (true) {
            skipWhitespace();
            array.add(parseValue(depth + 1));
            skipWhitespace();
            char c = peek();
            if (c == ',') {
                pos++;
                skipWhitespace();
                if (peek() == ']') {
                    throw error("Trailing comma in array");
                }
                continue;
            }
            if (c == ']') {
                pos++;
                return array;
            }
            throw error("Expected ',' or ']' in array");
        }
    }

    private String parseString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= input.length()) {
                throw error("Unterminated string");
            }
            char c = input.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= input.length()) {
                    throw error("Unterminated escape sequence");
                }
                char escape = input.charAt(pos++);
                switch (escape) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > input.length()) {
                            throw error("Truncated \\u escape");
                        }
                        String hex = input.substring(pos, pos + 4);
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException e) {
                            throw error("Invalid \\u escape '" + hex + "'");
                        }
                        pos += 4;
                        break;
                    default:
                        throw error("Invalid escape character '\\" + escape + "'");
                }
                continue;
            }
            if (c < 0x20) {
                throw error("Unescaped control character in string");
            }
            sb.append(c);
        }
    }

    private Number parseNumber() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        boolean integral = true;
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                integral = false;
                pos++;
            } else {
                break;
            }
        }
        String literal = input.substring(start, pos);
        try {
            if (integral) {
                // Prefer an exact long for ids, sizes and timestamps.
                return Long.valueOf(literal);
            }
            return Double.valueOf(literal);
        } catch (NumberFormatException e) {
            try {
                return Double.valueOf(literal);
            } catch (NumberFormatException e2) {
                throw error("Invalid number literal '" + literal + "'");
            }
        }
    }

    private void expectLiteral(String literal) {
        if (!input.startsWith(literal, pos)) {
            throw error("Expected literal '" + literal + "'");
        }
        pos += literal.length();
    }

    private void expect(char expected) {
        if (pos >= input.length() || input.charAt(pos) != expected) {
            throw error("Expected '" + expected + "'");
        }
        pos++;
    }

    private char peek() {
        if (pos >= input.length()) {
            throw error("Unexpected end of input");
        }
        return input.charAt(pos);
    }

    private void skipWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    private JsonException error(String message) {
        int line = 1;
        int column = 1;
        for (int i = 0; i < Math.min(pos, input.length()); i++) {
            if (input.charAt(i) == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
        }
        return new JsonException(message + " (line " + line + ", column " + column + ")");
    }
}
