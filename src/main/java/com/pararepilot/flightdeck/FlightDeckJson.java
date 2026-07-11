package com.pararepilot.flightdeck;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class FlightDeckJson {

    private final String json;
    private int index;

    private FlightDeckJson(String json) {
        this.json = json == null ? "" : json;
    }

    static Object parse(String json) {
        FlightDeckJson parser = new FlightDeckJson(json);
        Object value = parser.readValue();
        parser.skipWhitespace();

        if (!parser.end()) {
            throw parser.error("Unexpected trailing JSON content.");
        }

        return value;
    }

    private Object readValue() {
        skipWhitespace();

        if (end()) {
            throw error("Unexpected end of JSON.");
        }

        char c = current();

        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (c == '-' || Character.isDigit(c)) {
                    yield readNumber();
                }

                throw error("Unexpected JSON token.");
            }
        };
    }

    private Map<String, Object> readObject() {
        expect('{');
        skipWhitespace();

        Map<String, Object> object = new LinkedHashMap<>();

        if (peek('}')) {
            index++;
            return object;
        }

        while (true) {
            skipWhitespace();

            if (!peek('"')) {
                throw error("Expected JSON object key.");
            }

            String key = readString();
            skipWhitespace();
            expect(':');
            object.put(key, readValue());
            skipWhitespace();

            if (peek('}')) {
                index++;
                return object;
            }

            expect(',');
        }
    }

    private List<Object> readArray() {
        expect('[');
        skipWhitespace();

        List<Object> array = new ArrayList<>();

        if (peek(']')) {
            index++;
            return array;
        }

        while (true) {
            array.add(readValue());
            skipWhitespace();

            if (peek(']')) {
                index++;
                return array;
            }

            expect(',');
        }
    }

    private String readString() {
        expect('"');
        StringBuilder value = new StringBuilder();

        while (!end()) {
            char c = json.charAt(index++);

            if (c == '"') {
                return value.toString();
            }

            if (c != '\\') {
                value.append(c);
                continue;
            }

            if (end()) {
                throw error("Unterminated JSON escape.");
            }

            char escaped = json.charAt(index++);

            switch (escaped) {
                case '"' -> value.append('"');
                case '\\' -> value.append('\\');
                case '/' -> value.append('/');
                case 'b' -> value.append('\b');
                case 'f' -> value.append('\f');
                case 'n' -> value.append('\n');
                case 'r' -> value.append('\r');
                case 't' -> value.append('\t');
                case 'u' -> value.append(readUnicodeEscape());
                default -> throw error("Invalid JSON escape.");
            }
        }

        throw error("Unterminated JSON string.");
    }

    private char readUnicodeEscape() {
        if (index + 4 > json.length()) {
            throw error("Invalid JSON unicode escape.");
        }

        String hex = json.substring(index, index + 4);
        index += 4;

        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw error("Invalid JSON unicode escape.");
        }
    }

    private Number readNumber() {
        int start = index;

        if (peek('-')) {
            index++;
        }

        readDigits();

        boolean decimal = false;

        if (peek('.')) {
            decimal = true;
            index++;
            readDigits();
        }

        if (peek('e') || peek('E')) {
            decimal = true;
            index++;

            if (peek('+') || peek('-')) {
                index++;
            }

            readDigits();
        }

        String raw = json.substring(start, index);

        try {
            return decimal ? Double.parseDouble(raw) : Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw error("Invalid JSON number.");
        }
    }

    private void readDigits() {
        int start = index;

        while (!end() && Character.isDigit(current())) {
            index++;
        }

        if (start == index) {
            throw error("Expected JSON number digits.");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!json.startsWith(literal, index)) {
            throw error("Invalid JSON literal.");
        }

        index += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (!end() && Character.isWhitespace(current())) {
            index++;
        }
    }

    private void expect(char expected) {
        if (end() || current() != expected) {
            throw error("Expected '" + expected + "'.");
        }

        index++;
    }

    private boolean peek(char expected) {
        return !end() && current() == expected;
    }

    private char current() {
        return json.charAt(index);
    }

    private boolean end() {
        return index >= json.length();
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " Position " + index + ".");
    }
}
