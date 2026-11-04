package dev.oriel.engine.html;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/** Pull tokenizer for the supported HTML syntax. Tree construction owns the raw-text state transition. */
final class HtmlInput {
    enum Kind { START, END, TEXT, COMMENT, DOCTYPE, EOF }
    record Lexeme(Kind kind, String name, Map<String, String> attributes, String data, boolean closed, int offset) {}
    private final String input;
    private final BiConsumer<Integer, String> issue;
    private int cursor;

    HtmlInput(String input, BiConsumer<Integer, String> issue) { this.input = input; this.issue = issue; }

    Lexeme next() {
        int begin = cursor;
        if (cursor == input.length()) return token(Kind.EOF, "", begin);
        if (input.startsWith("<!--", cursor)) {
            int close = input.indexOf("-->", cursor + 4);
            if (close < 0) { issue.accept(begin, "Unclosed comment; recovered at EOF"); cursor = input.length(); }
            else cursor = close + 3;
            return token(Kind.COMMENT, "", begin);
        }
        if (input.regionMatches(true, cursor, "<!doctype", 0, 9)) {
            declarationEnd(begin); return token(Kind.DOCTYPE, "", begin);
        }
        if (input.startsWith("<![CDATA[", cursor)) {
            int close = input.indexOf("]]>", cursor + 9);
            String text = input.substring(cursor + 9, close < 0 ? input.length() : close);
            issue.accept(begin, "CDATA in HTML is a compatibility extension");
            cursor = close < 0 ? input.length() : close + 3;
            return token(Kind.TEXT, text, begin);
        }
        if (input.startsWith("<!", cursor) || input.startsWith("<?", cursor)) {
            issue.accept(begin, "Unsupported markup declaration; ignored");
            declarationEnd(begin); return token(Kind.COMMENT, "", begin);
        }
        boolean end = input.startsWith("</", cursor);
        int nameAt = cursor + (end ? 2 : 1);
        if (input.charAt(cursor) == '<' && nameAt < input.length() && letter(input.charAt(nameAt))) {
            cursor = nameAt;
            while (cursor < input.length() && !space(input.charAt(cursor)) && input.charAt(cursor) != '/' && input.charAt(cursor) != '>') cursor++;
            String name = input.substring(nameAt, cursor).toLowerCase(Locale.ROOT);
            if (end) { declarationEnd(begin); return new Lexeme(Kind.END, name, Map.of(), "", false, begin); }
            Map<String, String> attributes = new LinkedHashMap<>();
            boolean closed = false, terminated = false;
            while (cursor < input.length()) {
                whitespace(); if (cursor == input.length()) break;
                char c = input.charAt(cursor);
                if (c == '>') { cursor++; terminated = true; break; }
                if (c == '/') {
                    cursor++;
                    if (cursor < input.length() && input.charAt(cursor) == '>') { cursor++; closed = true; terminated = true; break; }
                    issue.accept(cursor - 1, "Unexpected slash in start tag"); continue;
                }
                int attributeAt = cursor;
                while (cursor < input.length()) {
                    c = input.charAt(cursor);
                    if (space(c) || c == '/' || c == '>' || c == '=') break;
                    if ((c == '\'' || c == '"' || c == '<') && cursor > attributeAt) break;
                    cursor++;
                }
                if (cursor == attributeAt) { issue.accept(cursor++, "Invalid attribute name"); continue; }
                String key = input.substring(attributeAt, cursor).toLowerCase(Locale.ROOT);
                if (key.indexOf('"') >= 0 || key.indexOf('\'') >= 0 || key.indexOf('<') >= 0) issue.accept(attributeAt, "Invalid character in attribute name");
                whitespace(); String value = "";
                if (cursor < input.length() && input.charAt(cursor) == '=') {
                    cursor++; whitespace(); int valueAt = cursor;
                    if (cursor < input.length() && (input.charAt(cursor) == '\'' || input.charAt(cursor) == '"')) {
                        char quote = input.charAt(cursor++); valueAt = cursor;
                        int stop = input.indexOf(quote, cursor);
                        if (stop < 0) { issue.accept(valueAt - 1, "Unclosed quoted attribute"); cursor = input.length(); }
                        else cursor = stop;
                        value = references(input.substring(valueAt, cursor), true, valueAt);
                        if (cursor < input.length()) cursor++;
                    } else {
                        while (cursor < input.length() && !space(input.charAt(cursor)) && input.charAt(cursor) != '>') cursor++;
                        value = references(input.substring(valueAt, cursor), true, valueAt);
                    }
                }
                if (attributes.putIfAbsent(key, value) != null) issue.accept(attributeAt, "Duplicate attribute '" + key + "'; first value kept");
            }
            if (!terminated) issue.accept(begin, "Unclosed start tag; recovered at EOF");
            return new Lexeme(Kind.START, name, attributes, "", closed, begin);
        }
        cursor++;
        while (cursor < input.length()) {
            if (input.charAt(cursor) == '<' && cursor + 1 < input.length()) {
                char c = input.charAt(cursor + 1);
                if (c == '!' || c == '?' || c == '/' || letter(c)) break;
            }
            cursor++;
        }
        return token(Kind.TEXT, references(input.substring(begin, cursor), false, begin), begin);
    }

    String raw(String tag, boolean references) {
        int start = cursor, end = input.length();
        for (int at = input.indexOf('<', cursor); at >= 0; at = input.indexOf('<', at + 1)) {
            int after = at + tag.length() + 2;
            if (input.regionMatches(true, at, "</" + tag, 0, tag.length() + 2)
                    && (after == input.length() || space(input.charAt(after)) || input.charAt(after) == '>' || input.charAt(after) == '/')) {
                end = at; cursor = after; declarationEnd(at); break;
            }
        }
        if (end == input.length()) { issue.accept(start, "Unclosed raw-text element '" + tag + "'"); cursor = end; }
        String text = input.substring(start, end);
        if (references) text = references(text, false, start);
        return tag.equals("textarea") && text.startsWith("\n") ? text.substring(1) : text;
    }

    private String references(String text, boolean attribute, int offset) {
        return CharacterReferences.decode(text, attribute, (at, message) -> issue.accept(offset + at, message));
    }

    private void declarationEnd(int begin) {
        int end = input.indexOf('>', cursor);
        if (end < 0) { issue.accept(begin, "Unclosed markup; recovered at EOF"); cursor = input.length(); }
        else cursor = end + 1;
    }
    private Lexeme token(Kind kind, String text, int at) { return new Lexeme(kind, "", Map.of(), text, false, at); }
    private void whitespace() { while (cursor < input.length() && space(input.charAt(cursor))) cursor++; }
    private static boolean space(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f'; }
    private static boolean letter(char c) { return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'; }
}
