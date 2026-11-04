package dev.oriel.engine.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** CSS Syntax 3 token consumption with original-source spans and a separate diagnostic channel. */
final class CssScanner {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]*\\.[0-9]+|[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private final String input;
    private final int[] lines;
    private int cursor;
    private CssScanner(String input) {
        this.input = input; lines = new int[input.length() + 1]; int line = 1;
        for (int i = 0; i < input.length(); i++) {
            lines[i] = line; char c = input.charAt(i);
            if (c == '\n' || c == '\f' || c == '\r' && (i + 1 == input.length() || input.charAt(i + 1) != '\n')) line++;
        }
        lines[input.length()] = line;
    }
    static List<Token> scan(String source) {
        CssScanner scanner = new CssScanner(source); List<Token> tokens = new ArrayList<>();
        while (scanner.cursor < source.length()) {
            if (source.startsWith("/*", scanner.cursor)) {
                int end = source.indexOf("*/", scanner.cursor + 2);
                if (end < 0) { scanner.problem(scanner.cursor, "Unclosed comment; recovered at EOF"); scanner.cursor = source.length(); }
                else scanner.cursor = end + 2;
            } else tokens.add(scanner.token());
        }
        return tokens;
    }
    private Token token() {
        int start = cursor, c = at(cursor);
        if (space(c)) { do { cursor++; } while (space(at(cursor))); return emit(Token.Type.WHITESPACE, "", 0, false, start); }
        if (c == '\'' || c == '"') return string(start, c);
        var number = DECIMAL.matcher(input).region(cursor, input.length());
        if (number.lookingAt()) {
            String spelling = number.group(); cursor = number.end(); double value = Double.parseDouble(spelling);
            boolean integer = spelling.indexOf('.') < 0 && spelling.indexOf('e') < 0 && spelling.indexOf('E') < 0;
            if (identifier(cursor)) return emit(Token.Type.DIMENSION, name(), value, integer, start);
            if (at(cursor) == '%') { cursor++; return emit(Token.Type.PERCENTAGE, "", value, integer, start); }
            return emit(Token.Type.NUMBER, "", value, integer, start);
        }
        if (input.startsWith("<!--", cursor)) { cursor += 4; return emit(Token.Type.CDO, "", 0, false, start); }
        if (input.startsWith("-->", cursor)) { cursor += 3; return emit(Token.Type.CDC, "", 0, false, start); }
        if (identifier(cursor)) {
            String name = name();
            if (at(cursor) != '(') return emit(Token.Type.IDENT, name, 0, false, start);
            cursor++; int afterParen = cursor;
            if (name.equalsIgnoreCase("url")) {
                while (space(at(cursor))) cursor++;
                if (at(cursor) != '\'' && at(cursor) != '"') return url(start);
                cursor = afterParen;
            }
            return emit(Token.Type.FUNCTION, name, 0, false, start);
        }
        cursor++;
        if (c == '#' && (namePart(at(cursor)) || escapeAt(cursor))) {
            boolean id = identifier(cursor); return emit(Token.Type.HASH, name(), 0, id, start);
        }
        if (c == '@' && identifier(cursor)) return emit(Token.Type.AT_KEYWORD, name(), 0, false, start);
        Token.Type type = switch (c) {
            case '(' -> Token.Type.OPEN_PAREN; case ')' -> Token.Type.CLOSE_PAREN;
            case '[' -> Token.Type.OPEN_SQUARE; case ']' -> Token.Type.CLOSE_SQUARE;
            case '{' -> Token.Type.OPEN_CURLY; case '}' -> Token.Type.CLOSE_CURLY;
            case ':' -> Token.Type.COLON; case ';' -> Token.Type.SEMICOLON; case ',' -> Token.Type.COMMA;
            default -> Token.Type.DELIM;
        };
        return emit(type, type == Token.Type.DELIM ? Character.toString(c) : "", 0, false, start);
    }
    private Token string(int start, int quote) {
        cursor++; StringBuilder value = new StringBuilder();
        while (cursor < input.length()) {
            int c = at(cursor);
            if (c == quote) { cursor++; return emit(Token.Type.STRING, value.toString(), 0, false, start); }
            if (newline(c)) { problem(start, "Newline in string; emitted bad-string token"); return emit(Token.Type.BAD_STRING, value.toString(), 0, false, start); }
            if (c == '\\') {
                cursor++;
                if (cursor == input.length()) break;
                if (newline(at(cursor))) { skipNewline(); continue; }
                value.appendCodePoint(escaped());
            } else { value.appendCodePoint(c); cursor += Character.charCount(c); }
        }
        problem(start, "Unclosed string; recovered at EOF"); return emit(Token.Type.STRING, value.toString(), 0, false, start);
    }
    private Token url(int start) {
        StringBuilder value = new StringBuilder();
        while (cursor < input.length()) {
            int c = at(cursor);
            if (c == ')') { cursor++; return emit(Token.Type.URL, value.toString(), 0, false, start); }
            if (space(c)) {
                while (space(at(cursor))) cursor++;
                if (cursor == input.length()) break;
                if (at(cursor) == ')') { cursor++; return emit(Token.Type.URL, value.toString(), 0, false, start); }
                return badUrl(start);
            }
            if (c == '\'' || c == '"' || c == '(' || c >= 0 && c < 32 || c == 127) return badUrl(start);
            if (c == '\\') {
                if (!escapeAt(cursor)) return badUrl(start);
                cursor++; value.appendCodePoint(escaped());
            } else { value.appendCodePoint(c); cursor += Character.charCount(c); }
        }
        problem(start, "Unclosed url(); recovered at EOF"); return emit(Token.Type.URL, value.toString(), 0, false, start);
    }
    private Token badUrl(int start) {
        problem(start, "Invalid unquoted url(); emitted bad-url token");
        while (cursor < input.length()) {
            if (at(cursor) == ')') { cursor++; break; }
            if (escapeAt(cursor)) { cursor++; escaped(); } else cursor++;
        }
        return emit(Token.Type.BAD_URL, "", 0, false, start);
    }
    private String name() {
        StringBuilder value = new StringBuilder();
        while (cursor < input.length()) {
            int c = at(cursor);
            if (namePart(c)) { value.appendCodePoint(c); cursor += Character.charCount(c); }
            else if (escapeAt(cursor)) { cursor++; value.appendCodePoint(escaped()); }
            else break;
        }
        return value.toString();
    }
    private int escaped() {
        if (cursor == input.length()) { problem(cursor, "Escape at EOF replaced by U+FFFD"); return 0xfffd; }
        int c = at(cursor), digit = hex(c);
        if (digit < 0) { cursor += Character.charCount(c); return c; }
        int value = 0, count = 0;
        while (count++ < 6 && (digit = hex(at(cursor))) >= 0) { value = value * 16 + digit; cursor++; }
        if (space(at(cursor))) { if (newline(at(cursor))) skipNewline(); else cursor++; }
        if (value == 0 || value > 0x10ffff || value >= 0xd800 && value <= 0xdfff) return 0xfffd;
        return value;
    }
    private int at(int index) {
        if (index >= input.length()) return -1;
        int c = input.codePointAt(index);
        return c == 0 || c >= 0xd800 && c <= 0xdfff ? 0xfffd : c;
    }
    private void skipNewline() { if (at(cursor++) == '\r' && at(cursor) == '\n') cursor++; }
    private boolean identifier(int index) {
        int first = at(index);
        if (first == '-') return nameStart(at(index + 1)) || at(index + 1) == '-' || escapeAt(index + 1);
        return nameStart(first) || escapeAt(index);
    }
    private boolean escapeAt(int index) { return at(index) == '\\' && !newline(at(index + 1)); }
    private static boolean nameStart(int c) { return c == '_' || c >= 128 || c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z'; }
    private static boolean namePart(int c) { return nameStart(c) || c == '-' || c >= '0' && c <= '9'; }
    private static boolean newline(int c) { return c == '\r' || c == '\n' || c == '\f'; }
    static boolean space(int c) { return newline(c) || c == ' ' || c == '\t'; }
    private static int hex(int c) { return c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1; }
    private Token emit(Token.Type type, String value, double number, boolean flag, int start) {
        return new Token(type, value, number, flag, input, start, cursor, lines[start]);
    }
    private void problem(int offset, String message) { CssIssues.report(input, offset, message); }
}
