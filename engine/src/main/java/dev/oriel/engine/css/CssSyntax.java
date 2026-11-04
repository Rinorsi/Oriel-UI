package dev.oriel.engine.css;

import dev.oriel.engine.Limits;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Iterative component-value construction and CSS Syntax declaration/rule consumption. */
final class CssSyntax {
    private CssSyntax() {}
    private record Frame(Token open, Token.Type closing, List<ComponentValue> values) {}

    static List<ComponentValue> components(String source) {
        List<Token> tokens = CssScanner.scan(source);
        List<ComponentValue> root = new ArrayList<>();
        ArrayDeque<Frame> frames = new ArrayDeque<>();
        int limit = Limits.current().maxCssNesting();
        for (int cursor = 0; cursor < tokens.size(); cursor++) {
            Token token = tokens.get(cursor);
            if (!frames.isEmpty() && token.type == frames.peek().closing()) {
                Frame frame = frames.pop(); list(root, frames).add(close(frame, token.end())); continue;
            }
            Token.Type closing = closing(token.type);
            if (closing == null) {
                if (token.is(Token.Type.CLOSE_CURLY) || token.is(Token.Type.CLOSE_PAREN) || token.is(Token.Type.CLOSE_SQUARE))
                    CssIssues.report(source, token.start(), "Unmatched closing delimiter");
                list(root, frames).add(token); continue;
            }
            if (frames.size() < limit) { frames.push(new Frame(token, closing, new ArrayList<>())); continue; }
            CssIssues.report(source, token.start(), "Maximum CSS nesting reached; nested contents omitted");
            ArrayDeque<Token.Type> skip = new ArrayDeque<>(); skip.push(closing); int end = token.end();
            while (cursor + 1 < tokens.size() && !skip.isEmpty()) {
                Token nested = tokens.get(++cursor); end = nested.end(); Token.Type expected = closing(nested.type);
                if (expected != null) skip.push(expected);
                else if (nested.type == skip.peek()) skip.pop();
            }
            list(root, frames).add(close(new Frame(token, closing, List.of()), end));
        }
        int end = tokens.isEmpty() ? 0 : tokens.getLast().end();
        while (!frames.isEmpty()) {
            Frame frame = frames.pop(); CssIssues.report(source, frame.open().start(), "Unclosed block or function; recovered at EOF");
            list(root, frames).add(close(frame, end));
        }
        return root;
    }
    private static List<ComponentValue> list(List<ComponentValue> root, ArrayDeque<Frame> frames) {
        return frames.isEmpty() ? root : frames.peek().values();
    }
    private static ComponentValue close(Frame frame, int end) {
        Token open = frame.open();
        if (open.is(Token.Type.FUNCTION)) return new ComponentValue.Func(open.lower, frame.values(), open.source(), open.start(), end, open.line());
        char kind = switch (open.type) { case OPEN_CURLY -> '{'; case OPEN_SQUARE -> '['; default -> '('; };
        return new ComponentValue.Block(kind, frame.values(), open.source(), open.start(), end, open.line());
    }
    private static Token.Type closing(Token.Type type) {
        return switch (type) {
            case FUNCTION, OPEN_PAREN -> Token.Type.CLOSE_PAREN;
            case OPEN_CURLY -> Token.Type.CLOSE_CURLY;
            case OPEN_SQUARE -> Token.Type.CLOSE_SQUARE;
            default -> null;
        };
    }
    static List<CssParser.Rule> rules(List<ComponentValue> input, boolean topLevel) {
        List<CssParser.Rule> output = new ArrayList<>(); int cursor = 0;
        while (cursor < input.size()) {
            ComponentValue first = input.get(cursor);
            if (space(first) || topLevel && first instanceof Token t && (t.is(Token.Type.CDO) || t.is(Token.Type.CDC))) { cursor++; continue; }
            String keyword = first instanceof Token t && t.is(Token.Type.AT_KEYWORD) ? t.lower : null;
            if (keyword != null) cursor++;
            List<ComponentValue> prelude = new ArrayList<>(); ComponentValue.Block block = null;
            while (cursor < input.size()) {
                ComponentValue value = input.get(cursor++);
                if (value instanceof ComponentValue.Block b && b.open() == '{') { block = b; break; }
                if (keyword != null && value instanceof Token t && t.is(Token.Type.SEMICOLON)) break;
                prelude.add(value);
            }
            if (keyword != null || block != null) output.add(new CssParser.Rule(keyword, trim(prelude), block, first.line(), first.column()));
            else CssIssues.report(first.source(), first.start(), "Qualified rule without a block discarded");
        }
        return output;
    }
    static List<CssParser.Declaration> declarations(List<ComponentValue> input, CssParser.Dropped dropped) {
        List<CssParser.Declaration> output = new ArrayList<>(); int cursor = 0;
        while (cursor < input.size()) {
            if (space(input.get(cursor)) || input.get(cursor) instanceof Token t && t.is(Token.Type.SEMICOLON)) { cursor++; continue; }
            int begin = cursor;
            // A nested at-rule ends at its block even without a following semicolon.
            boolean atRule = input.get(begin) instanceof Token t && t.is(Token.Type.AT_KEYWORD);
            while (cursor < input.size() && !(input.get(cursor) instanceof Token t && t.is(Token.Type.SEMICOLON))) {
                ComponentValue value = input.get(cursor++);
                if (atRule && value instanceof ComponentValue.Block b && b.open() == '{') break;
            }
            List<ComponentValue> item = trim(input.subList(begin, cursor));
            CssParser.Declaration declaration = declaration(item);
            if (declaration == null) {
                if (!item.isEmpty()) dropped.accept(ComponentValue.text(item), item.getFirst().line(), item.getFirst().column());
            } else output.add(declaration);
            if (cursor < input.size() && input.get(cursor) instanceof Token t && t.is(Token.Type.SEMICOLON)) cursor++;
        }
        return output;
    }
    private static CssParser.Declaration declaration(List<ComponentValue> item) {
        if (item.size() < 2 || !(item.getFirst() instanceof Token name) || !name.is(Token.Type.IDENT)) return null;
        int cursor = 1;
        while (cursor < item.size() && space(item.get(cursor))) cursor++;
        if (cursor == item.size() || !(item.get(cursor) instanceof Token colon) || !colon.is(Token.Type.COLON)) return null;
        List<ComponentValue> value = trim(item.subList(cursor + 1, item.size())); boolean important = false;
        if (!value.isEmpty() && value.getLast() instanceof Token tail && tail.isIdent("important")) {
            int bang = value.size() - 2;
            while (bang >= 0 && space(value.get(bang))) bang--;
            if (bang >= 0 && value.get(bang) instanceof Token t && t.isDelim('!')) { important = true; value = trim(value.subList(0, bang)); }
        }
        return new CssParser.Declaration(name.value.startsWith("--") ? name.value : name.lower, value, important, name.line(), name.column());
    }
    static List<ComponentValue> trim(List<ComponentValue> input) {
        int a = 0, b = input.size();
        while (a < b && space(input.get(a))) a++;
        while (b > a && space(input.get(b - 1))) b--;
        return List.copyOf(input.subList(a, b));
    }
    private static boolean space(ComponentValue value) { return value instanceof Token t && t.is(Token.Type.WHITESPACE); }
}
