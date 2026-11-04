package dev.oriel.engine.css;

import dev.oriel.engine.parse.ParserAudit;
import java.util.List;

/** CSS syntax entry points shared by stylesheets and the unchanged property-value parsers. */
final class CssParser {
    record Rule(String atKeyword, List<ComponentValue> prelude, ComponentValue.Block block, int line, int column) {
        Rule(String atKeyword, List<ComponentValue> prelude, ComponentValue.Block block, int line) { this(atKeyword, prelude, block, line, 0); }
        boolean isAtRule() { return atKeyword != null; }
    }
    record Declaration(String name, List<ComponentValue> value, boolean important, int line, int column) {
        Declaration(String name, List<ComponentValue> value, boolean important, int line) { this(name, value, important, line, 0); }
    }
    @FunctionalInterface interface Dropped {
        void accept(String text, int line);
        default void accept(String text, int line, int column) { accept(text, line); }
    }
    private CssParser() {}
    static List<ComponentValue> parseComponentValues(String source) { ParserAudit.mode(); return CssSyntax.components(source); }
    static List<Rule> parseRules(String source) { return parseRules(parseComponentValues(source), true); }
    static List<Rule> parseRules(List<ComponentValue> values, boolean topLevel) { ParserAudit.mode(); return CssSyntax.rules(values, topLevel); }
    static List<Declaration> parseDeclarations(String source) { return parseDeclarations(parseComponentValues(source)); }
    static List<Declaration> parseDeclarations(List<ComponentValue> values) { return parseDeclarations(values, (text, line) -> {}); }
    static List<Declaration> parseDeclarations(List<ComponentValue> values, Dropped dropped) { ParserAudit.mode(); return CssSyntax.declarations(values, dropped); }
    static List<ComponentValue> trim(List<ComponentValue> values) { return CssSyntax.trim(values); }
}
