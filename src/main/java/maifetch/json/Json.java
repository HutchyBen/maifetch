package maifetch.json;

import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Object parse(String input) {
        Parser parser = new Parser(input == null ? "" : input);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("unexpected trailing JSON content");
        }
        return value;
    }

    public static Object parse(Reader reader) throws IOException {
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, read);
        }
        return parse(builder.toString());
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            this.input = input;
        }

        private Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new IllegalArgumentException("unexpected end of JSON");
            }
            char ch = input.charAt(index);
            if (ch == '{') {
                return parseObject();
            }
            if (ch == '[') {
                return parseArray();
            }
            if (ch == '"') {
                return parseString();
            }
            if (ch == 't') {
                expect("true");
                return Boolean.TRUE;
            }
            if (ch == 'f') {
                expect("false");
                return Boolean.FALSE;
            }
            if (ch == 'n') {
                expect("null");
                return null;
            }
            if (ch == '-' || Character.isDigit(ch)) {
                return parseNumber();
            }
            throw new IllegalArgumentException("unexpected JSON character: " + ch);
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> object = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek('}')) {
                expect('}');
                return object;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw new IllegalArgumentException("object key must be a string");
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                object.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    expect('}');
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> values = new ArrayList<Object>();
            skipWhitespace();
            if (peek(']')) {
                expect(']');
                return values;
            }
            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    expect(']');
                    return values;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (!atEnd()) {
                char ch = input.charAt(index++);
                if (ch == '"') {
                    return builder.toString();
                }
                if (ch != '\\') {
                    builder.append(ch);
                    continue;
                }
                if (atEnd()) {
                    throw new IllegalArgumentException("unterminated JSON escape");
                }
                char escaped = input.charAt(index++);
                if (escaped == '"' || escaped == '\\' || escaped == '/') {
                    builder.append(escaped);
                } else if (escaped == 'b') {
                    builder.append('\b');
                } else if (escaped == 'f') {
                    builder.append('\f');
                } else if (escaped == 'n') {
                    builder.append('\n');
                } else if (escaped == 'r') {
                    builder.append('\r');
                } else if (escaped == 't') {
                    builder.append('\t');
                } else if (escaped == 'u') {
                    builder.append(parseUnicodeEscape());
                } else {
                    throw new IllegalArgumentException("unsupported JSON escape: " + escaped);
                }
            }
            throw new IllegalArgumentException("unterminated JSON string");
        }

        private char parseUnicodeEscape() {
            if (index + 4 > input.length()) {
                throw new IllegalArgumentException("incomplete unicode escape");
            }
            String hex = input.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("invalid unicode escape: " + hex, error);
            }
        }

        private Number parseNumber() {
            int start = index;
            if (peek('-')) {
                index++;
            }
            readDigits();
            if (peek('.')) {
                index++;
                readDigits();
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                readDigits();
            }
            String raw = input.substring(start, index);
            if (raw.indexOf('.') >= 0 || raw.indexOf('e') >= 0 || raw.indexOf('E') >= 0) {
                return new BigDecimal(raw);
            }
            try {
                return Integer.valueOf(raw);
            } catch (NumberFormatException ignored) {
                return Long.valueOf(raw);
            }
        }

        private void readDigits() {
            int start = index;
            while (!atEnd() && Character.isDigit(input.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw new IllegalArgumentException("expected JSON number digit");
            }
        }

        private void expect(String text) {
            if (!input.startsWith(text, index)) {
                throw new IllegalArgumentException("expected " + text);
            }
            index += text.length();
        }

        private void expect(char ch) {
            if (atEnd() || input.charAt(index) != ch) {
                throw new IllegalArgumentException("expected JSON character: " + ch);
            }
            index++;
        }

        private boolean peek(char ch) {
            return !atEnd() && input.charAt(index) == ch;
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char ch = input.charAt(index);
                if (ch != ' ' && ch != '\n' && ch != '\r' && ch != '\t') {
                    return;
                }
                index++;
            }
        }

        private boolean atEnd() {
            return index >= input.length();
        }
    }
}
