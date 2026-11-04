package dev.oriel.engine.css;

import dev.oriel.engine.parse.ParserAudit;
import java.util.ArrayList;
import java.util.List;

/** Stable syntax facade; property-level parsers consume the same component-value contract. */
final class CssParser {
    record Rule(String atKeyword, List<ComponentValue> prelude, ComponentValue.Block block, int line) {
        boolean isAtRule() { return atKeyword != null; }
    }
    record Declaration(String name, List<ComponentValue> value, boolean important, int line) {}
    @FunctionalInterface interface Dropped { void accept(String text, int line); }
    private CssParser() {}

    static List<ComponentValue> parseComponentValues(String source) {
        if (ParserAudit.mode() == ParserAudit.Mode.OLD) return LegacyCssParser.parseComponentValues(source);
        List<ComponentValue> result = CssSyntax.components(source);
        if (ParserAudit.mode() == ParserAudit.Mode.BOTH) ParserAudit.compare("css-components", "<value>",
                CssAudit.values(LegacyCssParser.parseComponentValues(source)), CssAudit.values(result));
        return result;
    }
    static List<Rule> parseRules(String source) {
        if (ParserAudit.mode() == ParserAudit.Mode.OLD) return LegacyCssParser.parseRules(source);
        List<Rule> result = CssSyntax.rules(CssSyntax.components(source), true);
        if (ParserAudit.mode() == ParserAudit.Mode.BOTH) ParserAudit.compare("css-rules", "<sheet>", CssAudit.rules(LegacyCssParser.parseRules(source)), CssAudit.rules(result));
        return result;
    }
    static List<Rule> parseRules(List<ComponentValue> values, boolean topLevel) {
        if (ParserAudit.mode() == ParserAudit.Mode.OLD) return LegacyCssParser.parseRules(values, topLevel);
        List<Rule> result = CssSyntax.rules(values, topLevel);
        if (ParserAudit.mode() == ParserAudit.Mode.BOTH) ParserAudit.compare("css-nested-rules", "<block>", CssAudit.rules(LegacyCssParser.parseRules(values, topLevel)), CssAudit.rules(result));
        return result;
    }
    static List<Declaration> parseDeclarations(String source) { return parseDeclarations(parseComponentValues(source)); }
    static List<Declaration> parseDeclarations(List<ComponentValue> values) { return parseDeclarations(values, (text, line) -> {}); }
    static List<Declaration> parseDeclarations(List<ComponentValue> values, Dropped dropped) {
        if (ParserAudit.mode() == ParserAudit.Mode.OLD) return LegacyCssParser.parseDeclarations(values, dropped);
        List<Declaration> result = CssSyntax.declarations(values, dropped);
        if (ParserAudit.mode() == ParserAudit.Mode.BOTH) ParserAudit.compare("css-declarations", "<declarations>",
                CssAudit.declarations(LegacyCssParser.parseDeclarations(values)), CssAudit.declarations(result));
        return result;
    }
    static List<ComponentValue> trim(List<ComponentValue> values) { return CssSyntax.trim(values); }
}
