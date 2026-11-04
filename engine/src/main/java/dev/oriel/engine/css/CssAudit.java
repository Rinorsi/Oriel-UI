package dev.oriel.engine.css;

import dev.oriel.engine.diag.Diagnostic;
import dev.oriel.engine.host.Host;
import dev.oriel.engine.parse.ParserAudit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/** Field-by-field syntax snapshots; object identity and implementation toString are never the oracle. */
final class CssAudit {
    private CssAudit() {}

    static Map<String, String> values(List<? extends ComponentValue> values) {
        Map<String, String> out = new TreeMap<>(); values(out, "values", values); return out;
    }
    private static void values(Map<String, String> out, String at, List<? extends ComponentValue> values) {
        out.put(at + "/size", String.valueOf(values.size()));
        for (int i = 0; i < values.size(); i++) {
            ComponentValue value = values.get(i); String key = at + "/" + i;
            out.put(key + "/start", String.valueOf(value.start())); out.put(key + "/end", String.valueOf(value.end()));
            out.put(key + "/line", String.valueOf(value.line())); out.put(key + "/text", value.text());
            if (value instanceof Token token) {
                out.put(key + "/kind", token.type.name()); out.put(key + "/value", token.value);
                out.put(key + "/number", Double.toString(token.number)); out.put(key + "/flag", Boolean.toString(token.flag));
            } else if (value instanceof ComponentValue.Func function) {
                out.put(key + "/kind", "function"); out.put(key + "/name", function.name()); values(out, key + "/args", function.args());
            } else if (value instanceof ComponentValue.Block block) {
                out.put(key + "/kind", "block"); out.put(key + "/open", String.valueOf(block.open())); values(out, key + "/body", block.body());
            }
        }
    }
    static Map<String, String> rules(List<CssParser.Rule> rules) {
        Map<String, String> out = new TreeMap<>(); out.put("rules/size", String.valueOf(rules.size()));
        for (int i = 0; i < rules.size(); i++) {
            var rule = rules.get(i); String at = "rules/" + i;
            out.put(at + "/at", rule.atKeyword()); out.put(at + "/line", String.valueOf(rule.line()));
            values(out, at + "/prelude", rule.prelude());
            values(out, at + "/block", rule.block() == null ? List.of() : List.of(rule.block()));
        }
        return out;
    }
    static Map<String, String> declarations(List<CssParser.Declaration> declarations) {
        Map<String, String> out = new TreeMap<>(); out.put("declarations/size", String.valueOf(declarations.size()));
        for (int i = 0; i < declarations.size(); i++) {
            var d = declarations.get(i); String at = "declarations/" + i;
            out.put(at + "/name", d.name()); out.put(at + "/line", String.valueOf(d.line()));
            out.put(at + "/important", Boolean.toString(d.important())); values(out, at + "/value", d.value());
        }
        return out;
    }
    private static Map<String, String> diagnostics(List<Diagnostic> diagnostics) {
        Map<String, String> out = new TreeMap<>();
        for (int i = 0; i < diagnostics.size(); i++) {
            Diagnostic d = diagnostics.get(i); String at = "diagnostics/" + i;
            out.put(at + "/code", d.code()); out.put(at + "/severity", d.severity().name());
            out.put(at + "/source", d.source()); out.put(at + "/line", String.valueOf(d.line()));
            out.put(at + "/column", String.valueOf(d.column())); out.put(at + "/message", d.message()); out.put(at + "/suggestion", d.suggestion());
        }
        return out;
    }
    static Stylesheet sheet(String css, String url, Host host, Consumer<Diagnostic> log) {
        List<Diagnostic> oldProblems = new ArrayList<>(), newProblems = new ArrayList<>();
        ParserAudit.using(ParserAudit.Mode.OLD, () -> Stylesheet.parse(css, url, host, oldProblems::add));
        Stylesheet result = ParserAudit.using(ParserAudit.Mode.NEW, () -> Stylesheet.parse(css, url, host, newProblems::add));
        ParserAudit.compare("css-stylesheet", url, rules(LegacyCssParser.parseRules(css)), rules(CssSyntax.rules(CssSyntax.components(css), true)));
        ParserAudit.compare("css-diagnostics", url, diagnostics(oldProblems), diagnostics(newProblems));
        newProblems.forEach(log); return result;
    }
    static List<Decl> inline(String css, String url, Host host, Consumer<Diagnostic> log) {
        List<Diagnostic> oldProblems = new ArrayList<>(), newProblems = new ArrayList<>();
        ParserAudit.using(ParserAudit.Mode.OLD, () -> Stylesheet.declarations(css, url, host, oldProblems::add));
        List<Decl> result = ParserAudit.using(ParserAudit.Mode.NEW, () -> Stylesheet.declarations(css, url, host, newProblems::add));
        ParserAudit.compare("css-inline", url, declarations(LegacyCssParser.parseDeclarations(css)), declarations(CssSyntax.declarations(CssSyntax.components(css), (text, line) -> {})));
        ParserAudit.compare("css-inline-diagnostics", url, diagnostics(oldProblems), diagnostics(newProblems));
        newProblems.forEach(log); return result;
    }
}
