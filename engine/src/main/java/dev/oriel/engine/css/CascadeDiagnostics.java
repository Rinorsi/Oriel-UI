package dev.oriel.engine.css;

import dev.oriel.engine.dom.Element;
import dev.oriel.engine.dom.PseudoElement;
import dev.oriel.engine.style.ComputedStyle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Allocated only for an explicit why-property request. No trace collector is installed in production. */
final class CascadeDiagnostics {
    private record Candidate(Decl declaration, RuleIndex.Entry rule, boolean inline, boolean matches, long rank) {}

    private CascadeDiagnostics() {}

    static Map<String, Object> explain(RuleIndex index, Element element, String name) {
        PseudoElement pseudo = PseudoElement.NONE;
        int slash = name.indexOf('/');
        if (slash >= 0) {
            String target = name.substring(0, slash);
            pseudo = java.util.Arrays.stream(PseudoElement.values()).filter(p -> p.cssName.equals(target)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown pseudo-element: " + target));
            name = name.substring(slash + 1);
        }
        Longhand property = Properties.longhand(name);
        if (property == null && !name.startsWith("--")) throw new IllegalArgumentException("Expected a longhand or custom property: " + name);
        List<Candidate> candidates = candidates(index, element, pseudo, name);
        Candidate winner = winner(candidates);
        List<Object> declarations = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Map<String, Object> row = describe(candidate);
            row.put("winner", candidate == winner);
            row.put("reason", !candidate.matches ? "selector-not-matched" : candidate == winner ? "winner" : reason(winner, candidate));
            declarations.add(row);
        }
        ComputedStyle base = baseStyle(element, pseudo);
        ComputedStyle used = switch (pseudo) {
            case NONE -> element.style; case BEFORE -> element.beforeStyle; case AFTER -> element.afterStyle; default -> base;
        };
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "cascade-explanation");
        result.put("page", element.ownerDocument().url());
        result.put("element", element.toString());
        result.put("pseudo", pseudo.cssName);
        result.put("property", name);
        result.put("schema", PropertySchema.named(name));
        result.put("declarations", declarations);
        result.put("baseValue", base == null ? null : StyleEngine.computedValue(base, name));
        result.put("usedValue", used == null ? null : StyleEngine.computedValue(used, name));
        boolean inherited = winner == null ? property == null || property.inherited()
                : winner.declaration.keyword == Decl.Keyword.INHERIT
                || winner.declaration.keyword == Decl.Keyword.UNSET && (property == null || property.inherited());
        Element parent = pseudo == PseudoElement.NONE ? element.parentElement() : element;
        if (winner != null && winner.declaration.hasVar && base != null) {
            String substituted = Vars.substitute(winner.declaration.value, base.customProperties::get);
            result.put("substitutedValue", substituted);
            result.put("variableResolution", substituted == null ? "unresolved-variable-unset" : "substituted");
            if (property == null) {
                inherited = false; // An invalid declared custom property removes its inherited value.
                if (substituted == null) result.put("variableResolution", "unresolved-custom-property");
            } else {
                List<ComponentValue> values = substituted == null ? null : CssParser.parseComponentValues(substituted);
                if (values != null && winner.declaration.pending != null) {
                    var parts = winner.declaration.pending.expand(values);
                    values = parts == null ? null : parts.get(property);
                }
                Decl.Keyword keyword = values == null ? Decl.Keyword.UNSET : Decl.keyword(values);
                if (keyword == null && !validValue(property, values, winner.declaration, element, parent, base)) {
                    keyword = Decl.Keyword.UNSET;
                    result.put("variableResolution", "invalid-after-variable-unset");
                } else if (values == null && substituted != null) {
                    result.put("variableResolution", "invalid-after-variable-unset");
                }
                inherited = keyword == Decl.Keyword.INHERIT || keyword == Decl.Keyword.UNSET && property.inherited();
            }
        }
        result.put("inheritedFrom", inherited && parent != null ? parent.toString() : null);
        List<Object> variables = new ArrayList<>();
        if (winner != null && winner.declaration.hasVar && base != null) {
            expandVariables(index, element, pseudo, winner.declaration, base.customProperties, variables, new HashSet<>(), 0);
        }
        result.put("variableExpansion", variables);
        result.put("note", "Used values include current animation sampling. Declaration ranking precedes invalid-at-computed-value fallback; it is not a performance probe.");
        return result;
    }

    private static ComputedStyle baseStyle(Element element, PseudoElement pseudo) {
        return switch (pseudo) {
            case NONE -> element.baseStyle; case BEFORE -> element.beforeBaseStyle; case AFTER -> element.afterBaseStyle;
            case PLACEHOLDER -> element.placeholderStyle; case FIRST_LETTER -> element.firstLetterStyle;
        };
    }

    private static boolean validValue(Longhand property, List<ComponentValue> values, Decl declaration,
                                      Element element, Element parent, ComputedStyle base) {
        var document = element.ownerDocument();
        ValueContext context = new ValueContext().environment(document.host(), document.viewportWidth(),
                document.viewportHeight(), document.devicePixelRatio());
        context.element(element, parent == null ? null : parent.baseStyle, document.documentElement().baseStyle.fontSize);
        if (!property.name.equals("font-size") && !property.name.equals("color")) context.own(base);
        context.baseUrl(declaration.baseUrl);
        return Decl.parse(property, values, context) != null;
    }

    private static List<Candidate> candidates(RuleIndex index, Element element, PseudoElement pseudo, String property) {
        List<Candidate> out = new ArrayList<>();
        Selector.MatchContext context = new Selector.MatchContext(element.ownerDocument().documentElement());
        for (RuleIndex.Entry rule : index.inspectionEntries()) {
            if (rule.selector().pseudoElement != pseudo) continue;
            for (Decl declaration : rule.decls()) {
                if (!name(declaration).equals(property)) continue;
                out.add(new Candidate(declaration, rule, false, rule.selector().matches(element, context),
                        Cascade.priority(declaration.important, rule.userAgent(), false, rule.layerRank())));
            }
        }
        List<Decl> inline = pseudo == PseudoElement.NONE ? ElementState.of(element).inline : null;
        if (inline != null) for (Decl declaration : inline) {
            if (name(declaration).equals(property)) out.add(new Candidate(declaration, null, true, true,
                    Cascade.priority(declaration.important, false, true, Integer.MAX_VALUE)));
        }
        return out;
    }

    private static String name(Decl declaration) { return declaration.isCustom() ? declaration.customName : declaration.property.name; }

    private static Candidate winner(List<Candidate> candidates) {
        Candidate best = null;
        for (Candidate candidate : candidates) {
            if (candidate.matches && (best == null || candidate.rank >= best.rank)) best = candidate;
        }
        return best;
    }

    private static Map<String, Object> describe(Candidate candidate) {
        Decl declaration = candidate.declaration;
        RuleIndex.Entry rule = candidate.rule;
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("selector", rule == null ? "<inline>" : rule.selector().toString());
        row.put("value", ComponentValue.text(declaration.value));
        row.put("url", declaration.baseUrl);
        row.put("line", declaration.value.isEmpty() ? 0 : declaration.value.get(0).line());
        row.put("important", declaration.important);
        row.put("origin", rule != null && rule.userAgent() ? "user-agent" : "author");
        row.put("inline", candidate.inline);
        row.put("layer", rule == null || rule.layer() == null ? "<unlayered>" : rule.layer().name);
        row.put("layerOrder", rule == null ? Integer.MAX_VALUE : rule.layerRank());
        row.put("specificity", rule == null ? 0 : rule.selector().specificity);
        row.put("sourceOrder", rule == null ? -1 : rule.key() & 0x7fffffffL);
        return row;
    }

    private static String reason(Candidate winner, Candidate loser) {
        if (winner.declaration.important != loser.declaration.important) return "importance";
        if ((winner.rule != null && winner.rule.userAgent()) != (loser.rule != null && loser.rule.userAgent())) return "origin";
        if (winner.inline != loser.inline) return "inline-priority";
        if (winner.rule != null && loser.rule != null) {
            if (winner.rule.layerRank() != loser.rule.layerRank()) return "layer-order";
            if (winner.rule.selector().specificity != loser.rule.selector().specificity) return "specificity";
        }
        return "source-order";
    }

    private static void expandVariables(RuleIndex index, Element element, PseudoElement pseudo, Decl declaration, Map<String, String> values,
                                        List<Object> trace, Set<String> seen, int depth) {
        if (depth >= 32 || trace.size() >= 256) return;
        Vars.substitute(declaration.value, name -> {
            if (trace.size() >= 256) return values.get(name);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("name", name);
            step.put("resolved", values.get(name));
            trace.add(step);
            if (seen.add(name)) {
                PseudoElement scope = pseudo;
                for (Element from = element; from != null;) {
                    Candidate source = winner(candidates(index, from, scope, name));
                    if (source != null) {
                        step.put("declaredOn", from.toString());
                        step.put("declaredPseudo", scope.cssName);
                        step.put("declaration", describe(source));
                        ComputedStyle sourceStyle = baseStyle(from, scope);
                        if (source.declaration.hasVar) expandVariables(index, from, scope, source.declaration,
                                sourceStyle == null ? values : sourceStyle.customProperties, trace, seen, depth + 1);
                        break;
                    }
                    if (scope != PseudoElement.NONE) scope = PseudoElement.NONE;
                    else from = from.parentElement();
                }
            } else step.put("alreadyVisited", true);
            return values.get(name);
        });
    }
}
